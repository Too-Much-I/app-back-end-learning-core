"""Render reviewable TEST ECS input locally. No AWS calls, credentials or deployment."""
import argparse
import copy
import json
import os
import re
from pathlib import Path

ENVIRONMENT = {
    'ANSWER_LEARNING_ENABLED': 'true',
    'ANSWER_LEARNING_GRADING_ENABLED': 'true',
    'ANSWER_LEARNING_DATASET_ID': 'ts-level-bank-40-per-part-20261007-v1',
    'ANSWER_LEARNING_CONTENT_VERSION': '20261012-cda52eb5001a',
    'ANSWER_LEARNING_S3_BUCKET': 'tosunsaeng-app-audio',
    'ANSWER_LEARNING_AWS_REGION': 'ap-northeast-2',
    'ANSWER_LEARNING_DAILY_SUBMISSIONS': '60',
    'ANSWER_LEARNING_POLL_MS': '1500',
}
READ_ONLY = {'taskDefinitionArn', 'revision', 'status', 'requiresAttributes',
             'compatibilities', 'registeredAt', 'registeredBy', 'deregisteredAt'}


def prepare(task, url):
    if not re.fullmatch(r'https://[a-z0-9]+\.lambda-url\.ap-northeast-2\.on\.aws/?', url):
        raise ValueError('Use a Seoul IAM Lambda URL verified for the test environment')
    if task.get('family') != 'tosunsaeng-learning-core-test':
        raise ValueError('Only the test task family is allowed')
    result = copy.deepcopy(task)
    containers = result.get('containerDefinitions', [])
    candidates = [c for c in containers if c.get('name') == 'tosunsaeng-learning-core-test']
    if len(candidates) != 1:
        raise ValueError('Expected exactly one Learning Core container')
    container = candidates[0]
    updates = {**ENVIRONMENT, 'ANSWER_LEARNING_LAMBDA_URL': url}
    if any(e['name'] in updates or e['name'] == 'SPRING_APPLICATION_JSON' for e in container.get('secrets', [])):
        raise ValueError('A secret could override learning configuration; review it separately')
    current = {e['name']: e['value'] for e in container.get('environment', [])}
    spring = json.loads(current.get('SPRING_APPLICATION_JSON', '{}'))
    if any('answer-learning' in k or 'answerLearning' in k for k in spring) or any(
        k in spring.get('app', {}) for k in ('answer-learning', 'answerLearning')
    ):
        raise ValueError('SPRING_APPLICATION_JSON overrides learning settings')
    # Do not create authentication/merge/deletion rollout approval by changing existing flags.
    current.update(updates)
    container['environment'] = [{'name': k, 'value': v} for k, v in sorted(current.items())]
    return {k: v for k, v in result.items() if k not in READ_ONLY}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--task-definition', type=Path, required=True)
    parser.add_argument('--lambda-url', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    task = json.loads(args.task_definition.read_text())
    rendered = prepare(task.get('taskDefinition', task), args.lambda_url)
    # Task definitions can contain unrelated private environment values. Never print their contents.
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with os.fdopen(os.open(args.output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), 'w') as stream:
        json.dump(rendered, stream, ensure_ascii=False, indent=2)
        stream.write('\n')
    args.output.chmod(0o600)
    print('Prepared a local test task definition. Nothing was deployed.')
