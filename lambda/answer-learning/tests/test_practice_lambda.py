import copy
import hashlib
import json
from pathlib import Path
from types import SimpleNamespace
from uuid import uuid4

import pytest
from pydantic import ValidationError

from app.practice_grading.contracts import Judgment, PracticeError
from app.practice_lambda.service import Evaluation, Service, public_job


class Store:
    def __init__(self): self.jobs = {}
    def get(self, key): return copy.deepcopy(self.jobs.get(key))
    def create(self, job): self.jobs.setdefault(job['id'], copy.deepcopy(job))
    def claim(self, key, lease):
        if self.jobs[key]['status'] != 'queued': return False
        token = str(uuid4())
        self.jobs[key].update(status='transcribing', leaseUntil=lease, leaseToken=token)
        return token
    def update(self, key, *, lease_token, **fields):
        row = self.jobs[key]
        if row.get('leaseToken') != lease_token or row['status'] not in ('transcribing', 'grading'):
            return False
        row.update(fields)
        return True
    def purge(self, submission_id, now):
        for attempt in (1, 2, 3):
            key = f'{submission_id}:{attempt}'
            self.jobs[key] = {'id': key, 'status': 'deleted', 'expiresAt': now + 30 * 86400}
    def expire(self, key, now):
        row = self.jobs[key]
        if row['status'] in ('grading', 'transcribing') and row['leaseUntil'] < now:
            row.update(status='failed', error={'code':'WORKER_INTERRUPTED','retryable':True})


class Media:
    def check(self, audio): pass
    def download(self, audio, path): path.write_bytes(b'audio')


class Queue:
    def __init__(self): self.sent = []; self.fail = False
    def send(self, job, group):
        if self.fail: raise RuntimeError('queue unavailable')
        self.sent.append(job)


class Provider:
    settings = SimpleNamespace(stt_model='fake-stt', judge_model='fake-judge')
    def __init__(self): self.stt = 0; self.judged = 0; self.fail = False; self.text = '85 and 95 dollars.'
    def require_ready(self): pass
    def transcribe(self, path): self.stt += 1; return self.text
    def judge(self, checklist, transcript, level):
        self.judged += 1
        if self.fail: raise PracticeError('UPSTREAM_FAILED','test',502,True)
        return Judgment(items=[{'id':x.id,'probability':.9} for x in checklist.items])


@pytest.fixture
def setup():
    c = {'lesson_id': 'synthetic_q8', 'version': 'test-v1', 'part': 4,
         'question': 'What are the two prices?', 'answer_seconds': 15, 'source': '85 and 95 dollars.',
         'items': [{'id': key, 'label': key, 'requirement': key,
                    'met_feedback': '확인했어요.', 'missing_feedback': '다시 확인해요.'} for key in ('price1', 'price2')]}
    sid = str(uuid4())
    body = {'submissionId':sid,'attempt':1,'lessonId':c['lesson_id'],'level':'IH',
            'contentVersion':'v1','audio':{'bucket':'test-bucket','key':f'recordings/{sid}/recording.m4a',
            'etag':'a'*32,'sha256':hashlib.sha256(b'audio').hexdigest()},'checklist':c}
    store, queue, provider = Store(), Queue(), Provider()
    now = [1000]
    service = Service(store, Media(), queue, bucket='test-bucket', prefix='recordings/', clock=lambda:now[0])
    return body, service, store, queue, provider, now


def process(service, request, provider):
    return service.process(request.job_id, lambda:provider, lambda *_:None)


def test_job_lifecycle_duplicate_delivery_and_result_shape(setup):
    body,s,store,q,p,now = setup
    r=Evaluation.model_validate(body)
    assert s.submit(r)['status']=='queued'
    s.submit(r)
    assert len(q.sent)==2
    assert process(s,r,p)
    assert process(s,r,p)
    assert (p.stt,p.judged)==(1,1)
    result=public_job(s.get(r.job_id))
    assert result['status']=='completed'
    assert result['result']['metCount']==2
    assert result['result']['checklistVersion']==body['checklist']['version']
    assert 'fingerprint' not in result and 'audio' not in result


def test_queue_failure_repaired_without_new_job(setup):
    body,s,store,q,p,now=setup
    r=Evaluation.model_validate(body);q.fail=True
    with pytest.raises(RuntimeError):s.submit(r)
    assert len(store.jobs)==1
    q.fail=False;s.submit(r);process(s,r,p)
    assert p.stt==1


def test_retry_is_explicit_reuses_transcript_and_binds_content(setup):
    body,s,store,q,p,now=setup;r=Evaluation.model_validate(body)
    s.submit(r);p.fail=True;process(s,r,p)
    process(s,r,p)
    assert p.judged==1
    body['attempt']=2;body['level']='AL'
    with pytest.raises(PracticeError,match='재시도 내용'):s.submit(Evaluation.model_validate(body))
    body['level']='IH';r2=Evaluation.model_validate(body)
    s.submit(r2);p.fail=False;process(s,r2,p)
    assert (p.stt,p.judged)==(1,2)
    body['attempt']=3
    with pytest.raises(PracticeError,match='재시도할 수'):s.submit(Evaluation.model_validate(body))


def test_stale_claim_does_not_automatically_recall_models(setup):
    body,s,store,q,p,now=setup;r=Evaluation.model_validate(body)
    s.submit(r);store.claim(r.job_id,1100)
    assert not process(s,r,p)
    now[0]=1101
    assert process(s,r,p)
    assert (p.stt,p.judged)==(0,0)
    assert s.get(r.job_id)['error']['code']=='WORKER_INTERRUPTED'


def test_unknown_audio_part1_mismatch_and_changed_payload_rejected(setup):
    body,s,store,q,p,now=setup
    body['audio']['bucket']='other-bucket'
    with pytest.raises(PracticeError):s.submit(Evaluation.model_validate(body))
    body['audio']['bucket']='test-bucket';s.submit(Evaluation.model_validate(body))
    body['audio']['sha256']='c'*64
    with pytest.raises(PracticeError):s.submit(Evaluation.model_validate(body))
    body['checklist']['part']=1
    with pytest.raises(ValidationError):Evaluation.model_validate(body)
    body['checklist']['part']=4;body['checklist']['lesson_id']='different'
    with pytest.raises(ValidationError):Evaluation.model_validate(body)


def test_empty_transcript_and_invalid_audio_do_not_judge(setup):
    body,s,store,q,p,now=setup;r=Evaluation.model_validate(body);s.submit(r);p.text=' '
    process(s,r,p)
    assert s.get(r.job_id)['status']=='no_speech' and p.judged==0


def test_third_failure_cannot_retry(setup):
    body,s,store,q,p,now=setup;p.fail=True
    for attempt in (1,2,3):
        body['attempt']=attempt;r=Evaluation.model_validate(body);s.submit(r);process(s,r,p)
    assert not s.get(r.job_id)['error']['retryable']
    assert (p.stt,p.judged)==(1,3)


def test_http_contract_requires_iam_and_handles_payload_errors(setup, monkeypatch):
    from app.practice_lambda import handler
    body,s,*_=setup
    monkeypatch.setattr(handler,'service',lambda:s)
    event={'requestContext':{'http':{'method':'POST'}},'rawPath':'/v1/learning/evaluations','body':json.dumps(body)}
    assert handler.intake(event,None)['statusCode']==403
    event['requestContext']['authorizer']={'iam':{'userArn':'arn:aws:iam::123:role/backend'}}
    accepted=handler.intake(event,None)
    assert accepted['statusCode']==202
    job=json.loads(accepted['body'])['result']['jobId']
    event['body']='{'
    assert handler.intake(event,None)['statusCode']==422
    event['requestContext']['http']['method']='GET';event['rawPath']='/v1/learning/evaluations/'+job
    assert handler.intake(event,None)['statusCode']==200


def test_aws_media_checks_checksum_and_closes_stream(tmp_path):
    import io
    from app.practice_lambda.aws import Media
    from app.practice_lambda.service import Audio
    audio=Audio(bucket='bucket',key='object',etag='a'*32,sha256='0'*64)
    stream=io.BytesIO(b'changed')
    class S3:
        def head_object(self, **kw):
            assert kw['IfMatch']==f'"{audio.etag}"'
            return {'ContentLength':7,'ContentType':'audio/mp4'}
        def get_object(self, **kw):
            assert kw['IfMatch']==f'"{audio.etag}"'
            return {'Body':stream}
    with pytest.raises(PracticeError,match='변경'):
        Media(S3()).download(audio,tmp_path/'audio.m4a')
    assert stream.closed


def test_sqs_record_failure_is_returned_to_queue(setup, monkeypatch):
    from app.practice_lambda import handler
    body,s,*_=setup
    monkeypatch.setattr(handler,'service',lambda:s)
    result=handler.worker({'Records':[{'messageId':'m1','body':'{'}]},None)
    assert result=={'batchItemFailures':[{'itemIdentifier':'m1'}]}


def test_infrastructure_is_separate_private_and_bounded():
    template=json.loads(Path('infra/practice-learning-stack.json').read_text())
    r=template['Resources']
    assert r['Url']['Properties']['AuthType']=='AWS_IAM'
    assert r['Worker']['Properties']['Timeout']==120
    assert r['Worker']['Properties']['ReservedConcurrentExecutions']==2
    assert r['Jobs']['Properties']['TimeToLiveSpecification']['Enabled']
    assert r['Queue']['Properties']['VisibilityTimeout']>180
    assert 's3:PutObject' not in json.dumps(r['WorkerRole'])
    assert 'secretsmanager:GetSecretValue' not in json.dumps(r['IntakeRole'])


def test_purge_clears_all_attempts_and_rejects_delayed_delivery(setup):
    body, service, store, queue, provider, now = setup
    request = Evaluation.model_validate(body)
    service.submit(request)
    process(service, request, provider)
    assert service.purge(body['submissionId'])['purged']
    assert all(set(job) == {'id', 'status', 'expiresAt'} for job in store.jobs.values())
    assert process(service, request, provider)
    assert provider.judged == 1
    for attempt in (1, 2, 3):
        body['attempt'] = attempt
        with pytest.raises(PracticeError, match='삭제된'):
            service.submit(Evaluation.model_validate(body))


def test_purge_during_transcription_fences_transcript_and_skips_judge(setup):
    body, service, store, queue, provider, now = setup
    request = Evaluation.model_validate(body)
    service.submit(request)
    def transcribe(_):
        service.purge(body['submissionId'])
        return 'Sensitive response'
    provider.transcribe = transcribe
    assert process(service, request, provider)
    assert provider.judged == 0
    assert 'Sensitive response' not in json.dumps(store.jobs)


def test_purge_during_judgment_cannot_resurrect_result(setup):
    body, service, store, queue, provider, now = setup
    request = Evaluation.model_validate(body)
    service.submit(request)
    original = provider.judge
    def judge(*args):
        service.purge(body['submissionId'])
        return original(*args)
    provider.judge = judge
    assert process(service, request, provider)
    assert all(job['status'] == 'deleted' and 'result' not in job for job in store.jobs.values())


def test_delete_endpoint_requires_iam_and_returns_matching_proof(setup, monkeypatch):
    from app.practice_lambda import handler
    body, service, *_ = setup
    monkeypatch.setattr(handler, 'service', lambda: service)
    event = {'requestContext': {'http': {'method': 'DELETE'}},
             'rawPath': '/v1/learning/evaluations/' + body['submissionId']}
    assert handler.intake(event, None)['statusCode'] == 403
    event['requestContext']['authorizer'] = {'iam': {'userArn': 'arn:aws:iam::123:role/backend'}}
    result = handler.intake(event, None)
    assert result['statusCode'] == 200
    assert json.loads(result['body'])['result'] == {'submissionId': body['submissionId'], 'purged': True}
