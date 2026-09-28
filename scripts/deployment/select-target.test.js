const { test } = require('node:test');
const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const path = require('node:path');
const { readFileSync } = require('node:fs');

const sha = 'a'.repeat(40);
const mainRole = 'arn:aws:iam::123456789012:role/main-deploy';
const testRole = 'arn:aws:iam::123456789012:role/test-deploy';
function select(ref, extra = {}) {
  return spawnSync('bash', [path.join(__dirname, 'select-target.sh')], {
    encoding: 'utf8',
    env: { ...process.env, GITHUB_REF: ref, GITHUB_SHA: sha,
      MAIN_ROLE_ARN: mainRole, TEST_ROLE_ARN: testRole, ...extra },
  });
}
function values(result) {
  assert.equal(result.status, 0, result.stderr);
  return Object.fromEntries(result.stdout.trim().split('\n').map(line => {
    const i = line.indexOf('=');
    return [line.slice(0, i), line.slice(i + 1)];
  }));
}
test('main preserves existing service, role, URL and image aliases', () => {
  const env = values(select('refs/heads/main'));
  assert.equal(env.ECS_SERVICE, 'tosunsaeng-learning-core-service');
  assert.equal(env.TASK_FAMILY, 'tosunsaeng-learning-core');
  assert.equal(env.CONTAINER_NAME, env.TASK_FAMILY);
  assert.equal(env.HEALTH_URL, 'https://api-staging.to-teacher.com/actuator/health');
  assert.equal(env.AWS_DEPLOY_ROLE_ARN, mainRole);
  assert.equal(env.IMAGE_ALIAS, 'staging');
  assert.equal(env.IMAGE_TAG, sha);
});
test('develop uses only test targets including namespaced commit tag', () => {
  const env = values(select('refs/heads/develop'));
  assert.equal(env.ECS_SERVICE, 'tosunsaeng-learning-core-test-service');
  assert.equal(env.TASK_FAMILY, 'tosunsaeng-learning-core-test');
  assert.equal(env.CONTAINER_NAME, env.TASK_FAMILY);
  assert.equal(env.HEALTH_URL, 'https://api-test.to-teacher.com/actuator/health');
  assert.equal(env.AWS_DEPLOY_ROLE_ARN, testRole);
  assert.equal(env.IMAGE_ALIAS, 'test');
  assert.equal(env.IMAGE_TAG, `test-${sha}`);
});
for (const ref of ['refs/heads/feature/foo', 'refs/tags/main', 'refs/heads/devlop', '']) {
  test(`reject unsupported ref: ${ref}`, () => {
    const result = select(ref);
    assert.notEqual(result.status, 0);
    assert.equal(result.stdout, '');
  });
}
test('missing test role never falls back to main role', () => {
  assert.notEqual(select('refs/heads/develop', { TEST_ROLE_ARN: '' }).status, 0);
  assert.equal(select('refs/heads/main', { TEST_ROLE_ARN: '' }).status, 0);
});
test('invalid SHA and multiline role cannot inject environment settings', () => {
  for (const extra of [{ GITHUB_SHA: 'bad' }, { TEST_ROLE_ARN: `${testRole}\nECS_SERVICE=production` }]) {
    const result = select('refs/heads/develop', extra);
    assert.notEqual(result.status, 0);
    assert.equal(result.stdout, '');
  }
});
test('develop rejects the configured main deployment role', () => {
  const result = select('refs/heads/develop', { TEST_ROLE_ARN: mainRole });
  assert.notEqual(result.status, 0);
  assert.equal(result.stdout, '');
});
test('workflow uses selected target, preflights before push and deploys digest', () => {
  const workflow = readFileSync(path.join(__dirname, '../../.github/workflows/deploy-staging.yml'), 'utf8');
  assert.match(workflow, /- main\n\s+- develop/);
  assert.match(workflow, /if: github.ref == 'refs\/heads\/main' \|\| github.ref == 'refs\/heads\/develop'/);
  assert.match(workflow, /role-to-assume: \$\{\{ env.AWS_DEPLOY_ROLE_ARN \}\}/);
  assert.match(workflow, /service: \$\{\{ env.ECS_SERVICE \}\}/);
  assert.match(workflow, /\.family == \$family/);
  assert.ok(workflow.indexOf('name: Download current task definition') < workflow.indexOf('name: Build and push image'));
  assert.match(workflow, /@\$\{IMAGE_DIGEST\}/);
});
