"""Small AWS adapters. Job snapshots/results use JSON strings to preserve number types."""
import hashlib
import json
import os
from pathlib import Path
from uuid import uuid4

import boto3
from botocore.exceptions import ClientError

from app.practice_grading.contracts import PracticeError
from app.practice_grading.provider import PracticeProvider
from app.practice_grading.settings import PracticeSettings, OPENAI_DECISIONS_MODEL
from app.practice_lambda.service import MAX_AUDIO_BYTES, error_value


def conditional_failed(exc):
    return exc.response['Error']['Code'] == 'ConditionalCheckFailedException'


class Store:
    def __init__(self, table):
        self.table = table

    def get(self, job_id):
        row = self.table.get_item(Key={'id': job_id}, ConsistentRead=True).get('Item')
        if not row:
            return None
        return {k.removesuffix('Json') if k.endswith('Json') else k:
                json.loads(v) if k.endswith('Json') else v for k, v in row.items()}

    def create(self, job):
        row = {k + 'Json' if isinstance(v, (dict, list)) or v is None else k:
               json.dumps(v, ensure_ascii=False) if isinstance(v, (dict, list)) or v is None else v
               for k, v in job.items()}
        try:
            self.table.put_item(Item=row, ConditionExpression='attribute_not_exists(id)')
        except ClientError as exc:
            if not conditional_failed(exc):
                raise

    def purge(self, submission_id, now):
        # Unconditional replacement is the fence: no payload, result, transcript or audio survives.
        # All permitted attempts are reserved, so a delayed retry cannot create another job.
        for attempt in (1, 2, 3):
            self.table.put_item(Item={'id': f'{submission_id}:{attempt}', 'status': 'deleted',
                                      'expiresAt': now + 30 * 86400})

    def claim(self, job_id, lease):
        token = str(uuid4())
        try:
            self.table.update_item(Key={'id': job_id},
                UpdateExpression='SET #s = :running, leaseUntil = :lease, leaseToken = :token',
                ConditionExpression='#s = :queued', ExpressionAttributeNames={'#s': 'status'},
                ExpressionAttributeValues={':running': 'transcribing', ':queued': 'queued', ':lease': lease, ':token': token})
            return token
        except ClientError as exc:
            if not conditional_failed(exc):
                raise
            return False

    def update(self, job_id, *, lease_token, **fields):
        names, values, expressions = {}, {}, []
        for i, (key, value) in enumerate(fields.items()):
            structured = isinstance(value, (dict, list)) or value is None
            names[f'#k{i}'] = key + 'Json' if structured else key
            values[f':v{i}'] = json.dumps(value, ensure_ascii=False) if structured else value
            expressions.append(f'#k{i} = :v{i}')
        names['#status'] = 'status'
        values.update({':token': lease_token, ':stt': 'transcribing', ':judge': 'grading'})
        try:
            self.table.update_item(Key={'id': job_id}, UpdateExpression='SET ' + ', '.join(expressions),
                ExpressionAttributeNames=names, ExpressionAttributeValues=values,
                ConditionExpression='leaseToken = :token AND (#status = :stt OR #status = :judge)')
            return True
        except ClientError as exc:
            if not conditional_failed(exc):
                raise
            return False

    def expire(self, job_id, now):
        error = error_value('WORKER_INTERRUPTED', '채점 처리가 중단됐어요. 다시 제출해 주세요.', not job_id.endswith(':3'))
        try:
            self.table.update_item(Key={'id': job_id},
                UpdateExpression='SET #s = :failed, errorJson = :error',
                ConditionExpression='(#s = :stt OR #s = :judge) AND leaseUntil < :now',
                ExpressionAttributeNames={'#s': 'status'},
                ExpressionAttributeValues={':failed': 'failed', ':stt': 'transcribing', ':judge': 'grading',
                    ':error': json.dumps(error, ensure_ascii=False), ':now': now})
        except ClientError as exc:
            if not conditional_failed(exc):
                raise


class Media:
    def __init__(self, client):
        self.client = client

    def check(self, audio):
        try:
            obj = self.client.head_object(Bucket=audio.bucket, Key=audio.key, IfMatch=f'"{audio.etag}"' )
        except ClientError as exc:
            if exc.response['Error']['Code'] in ('404', 'NoSuchKey', 'PreconditionFailed', '412'):
                raise PracticeError('AUDIO_NOT_FOUND', '녹음 파일을 확인할 수 없어요.', 422) from exc
            raise
        if not 1 <= obj['ContentLength'] <= MAX_AUDIO_BYTES or obj.get('ContentType') not in ('audio/mp4', 'audio/x-m4a'):
            raise PracticeError('INVALID_AUDIO', '녹음 파일의 형식이나 크기를 확인해 주세요.', 422)

    def download(self, audio, path):
        self.check(audio)
        obj = self.client.get_object(Bucket=audio.bucket, Key=audio.key, IfMatch=f'"{audio.etag}"' )
        try:
            data = obj['Body'].read(MAX_AUDIO_BYTES + 1)
        finally:
            obj['Body'].close()
        if len(data) > MAX_AUDIO_BYTES or hashlib.sha256(data).hexdigest() != audio.sha256:
            raise PracticeError('AUDIO_CHANGED', '제출한 녹음 파일이 변경됐어요.', 409)
        path.write_bytes(data)


class Queue:
    def __init__(self, client, url):
        self.client, self.url = client, url

    def send(self, job_id, submission_id):
        # No FIFO dedup ID: every resend repairs a potentially lost delivery. The worker's
        # durable atomic claim is the idempotency boundary, including after five minutes.
        self.client.send_message(QueueUrl=self.url, MessageBody=json.dumps({'jobId': job_id}))


def provider_factory():
    data = json.loads(boto3.client('secretsmanager').get_secret_value(
        SecretId=os.environ['PRACTICE_SECRET_ARN'])['SecretString'])
    key = data.get('OPENAI_API_KEY', '')
    settings = PracticeSettings(Path('/tmp'), Path('/tmp'), '', key,
        os.environ.get('PRACTICE_STT_MODEL', 'gpt-transcribe'), OPENAI_DECISIONS_MODEL)
    return PracticeProvider(settings)
