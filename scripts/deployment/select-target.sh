#!/usr/bin/env bash
set -euo pipefail

# Emit only fixed deployment settings and a validated role ARN to GITHUB_ENV.
case "${GITHUB_REF:-}" in
  refs/heads/main)
    target=main
    service=tosunsaeng-learning-core-service
    family=tosunsaeng-learning-core
    health=https://api-staging.to-teacher.com/actuator/health
    alias=staging
    tag="${GITHUB_SHA:-}"
    role="${MAIN_ROLE_ARN:-}"
    ;;
  refs/heads/develop)
    if [[ -n "${TEST_ROLE_ARN:-}" && "${TEST_ROLE_ARN}" == "${MAIN_ROLE_ARN:-}" ]]; then
      echo '::error::Test deployment requires a separate AWS role' >&2
      exit 1
    fi
    target=test
    service=tosunsaeng-learning-core-test-service
    family=tosunsaeng-learning-core-test
    health=https://api-test.to-teacher.com/actuator/health
    alias=test
    tag="test-${GITHUB_SHA:-}"
    role="${TEST_ROLE_ARN:-}"
    ;;
  *) echo '::error::Deployment is allowed only from main or develop' >&2; exit 1 ;;
esac

if [[ ! "${GITHUB_SHA:-}" =~ ^[0-9a-f]{40}$ ]]; then
  echo '::error::Invalid commit SHA' >&2
  exit 1
fi
if [[ ! "$role" =~ ^arn:aws:iam::[0-9]{12}:role/[A-Za-z0-9_+=,.@/-]+$ ]]; then
  echo '::error::Configure the branch-specific AWS role variable (AWS_ROLE_ARN / AWS_TEST_ROLE_ARN)' >&2
  exit 1
fi
printf '%s\n' "DEPLOY_TARGET=$target" "ECS_SERVICE=$service" \
  "TASK_FAMILY=$family" "CONTAINER_NAME=$family" "HEALTH_URL=$health" \
  "IMAGE_ALIAS=$alias" "IMAGE_TAG=$tag" "AWS_DEPLOY_ROLE_ARN=$role"
