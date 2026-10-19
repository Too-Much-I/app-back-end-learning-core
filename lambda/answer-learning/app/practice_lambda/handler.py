import base64
import json
import os
from functools import lru_cache

import boto3
from pydantic import ValidationError

from app.practice_grading.contracts import PracticeError
from app.practice_lambda.aws import Media, Queue, Store, provider_factory
from app.practice_lambda.service import Evaluation, Service, public_job


@lru_cache(maxsize=1)
def service():
    return Service(Store(boto3.resource('dynamodb').Table(os.environ['PRACTICE_TABLE'])),
        Media(boto3.client('s3')), Queue(boto3.client('sqs'), os.environ['PRACTICE_QUEUE_URL']),
        bucket=os.environ['PRACTICE_AUDIO_BUCKET'], prefix=os.environ['PRACTICE_AUDIO_PREFIX'])


def response(status, result=None, code='SUCCESS', message='완료'):
    return {'statusCode': status, 'headers': {'Content-Type': 'application/json; charset=utf-8',
            'Cache-Control': 'no-store'}, 'body': json.dumps({'isSuccess': status < 400,
            'code': code, 'message': message, 'result': result}, ensure_ascii=False)}


def intake(event, _context):
    # Function URL has AWS_IAM auth. These credentials belong to the backend, never the app.
    try:
        context = event.get('requestContext', {})
        if not context.get('authorizer', {}).get('iam', {}).get('userArn'):
            return response(403, code='UNAUTHORIZED', message='백엔드 IAM 인증이 필요합니다.')
        method = context.get('http', {}).get('method', '')
        path = event.get('rawPath', '')
        if method == 'GET' and path == '/health':
            return response(200, {'service': 'answer-learning-grading', 'contractVersion': 'v1',
                                  'judge': 'gpt-6-luna', 'auth': 'AWS_IAM', 'deletionContract': 'v1'})
        if method == 'POST' and path == '/v1/learning/evaluations':
            raw = event.get('body') or ''
            if len(raw) > 90000:
                return response(413, code='BODY_TOO_LARGE', message='요청 본문이 너무 큽니다.')
            body = base64.b64decode(raw, validate=True) if event.get('isBase64Encoded') else raw
            request = Evaluation.model_validate_json(body)
            return response(202, public_job(service().submit(request)))
        if method == 'DELETE' and path.startswith('/v1/learning/evaluations/'):
            submission_id = path.removeprefix('/v1/learning/evaluations/')
            return response(200, service().purge(submission_id))
        if method == 'GET' and path.startswith('/v1/learning/evaluations/'):
            job_id = path.removeprefix('/v1/learning/evaluations/')
            return response(200, public_job(service().get(job_id)))
        return response(404, code='NOT_FOUND', message='경로를 확인해 주세요.')
    except (ValidationError, ValueError, TypeError):
        return response(422, code='INVALID_REQUEST', message='제출 정보와 체크리스트 형식을 확인해 주세요.')
    except PracticeError as exc:
        return response(exc.status, code=exc.code, message=exc.message)
    except Exception:
        # Never log event, transcript, secret or provider exception body.
        return response(503, code='SERVICE_UNAVAILABLE', message='접수가 지연됐어요. 같은 ID로 다시 요청해 주세요.')


def worker(event, _context):
    failures = []
    for record in event.get('Records', []):
        try:
            done = service().process(json.loads(record['body'])['jobId'], provider_factory)
            if not done:
                failures.append({'itemIdentifier': record['messageId']})
        except Exception:
            failures.append({'itemIdentifier': record['messageId']})
    return {'batchItemFailures': failures}
