// Keep push-based deployment so the existing branch-scoped AWS OIDC trust is unchanged.
module.exports = async function allowDeployment({ github, context, core }) {
  const { eventName, ref, sha, repo } = context;
  if (!['refs/heads/main', 'refs/heads/develop'].includes(ref)) return false;
  if (eventName === 'workflow_dispatch') return true;
  if (eventName !== 'push') return false;
  if (ref === 'refs/heads/main') return true;

  const pulls = await github.paginate(github.rest.repos.listPullRequestsAssociatedWithCommit, {
    ...repo, commit_sha: sha, per_page: 100,
  });
  const allowed = pulls.some(pr => pr.merged_at && pr.merge_commit_sha === sha
    && pr.base.ref === 'develop'
    && pr.base.repo?.full_name === `${repo.owner}/${repo.repo}`);
  if (!allowed) core.notice('Skipping test deployment: develop HEAD is not a merged PR commit.');
  return allowed;
};
