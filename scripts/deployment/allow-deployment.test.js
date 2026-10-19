const { test } = require('node:test');
const assert = require('node:assert/strict');
const allowDeployment = require('./allow-deployment.cjs');

const sha = 'a'.repeat(40);
const merged = { merged_at: '2026-09-30', merge_commit_sha: sha,
  base: { ref: 'develop', repo: { full_name: 'owner/lc' } } };
async function check(overrides = {}, pulls = [], fail = false) {
  let calls = 0;
  const result = await allowDeployment({
    context: { eventName: 'push', ref: 'refs/heads/develop', sha,
      repo: { owner: 'owner', repo: 'lc' }, ...overrides },
    github: { rest: { repos: { listPullRequestsAssociatedWithCommit: 'endpoint' } },
      paginate: async (endpoint, args) => {
        calls++;
        assert.equal(endpoint, 'endpoint');
        assert.equal(args.commit_sha, sha);
        if (fail) throw new Error('API unavailable');
        return pulls;
      } },
    core: { notice() {} },
  });
  return { result, calls };
}
test('develop deploys only the exact merged PR result (merge/squash/rebase final SHA)', async () => {
  assert.deepEqual(await check({}, [merged]), { result: true, calls: 1 });
});
test('direct push, open/closed-unmerged PR and pre-merge commits do not deploy', async () => {
  for (const pulls of [[], [{ ...merged, merged_at: null }],
    [{ ...merged, merge_commit_sha: 'b'.repeat(40) }]]) {
    assert.equal((await check({}, pulls)).result, false);
  }
});
test('other base branches and repositories do not qualify', async () => {
  for (const base of [{ ...merged.base, ref: 'main' },
    { ...merged.base, repo: { full_name: 'other/lc' } }]) {
    assert.equal((await check({}, [{ ...merged, base }])).result, false);
  }
});
test('main push and explicit manual deployment retain existing behavior', async () => {
  for (const overrides of [{ ref: 'refs/heads/main' },
    { eventName: 'workflow_dispatch' },
    { eventName: 'workflow_dispatch', ref: 'refs/heads/main' }]) {
    assert.deepEqual(await check(overrides), { result: true, calls: 0 });
  }
});
test('feature branches, tags and other events cannot deploy', async () => {
  for (const overrides of [{ ref: 'refs/heads/feature/test' },
    { ref: 'refs/tags/develop' }, { eventName: 'pull_request' },
    { eventName: 'workflow_dispatch', ref: 'refs/heads/feature/test' }]) {
    assert.deepEqual(await check(overrides), { result: false, calls: 0 });
  }
});
test('GitHub API errors fail closed', async () => {
  await assert.rejects(check({}, [], true), /API unavailable/);
});
