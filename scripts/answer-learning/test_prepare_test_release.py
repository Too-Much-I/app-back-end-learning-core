import copy
import unittest
from prepare_test_release import prepare

URL = 'https://fixture.lambda-url.ap-northeast-2.on.aws/'


class PrepareReleaseTest(unittest.TestCase):
    def task(self):
        return {'family': 'tosunsaeng-learning-core-test', 'revision': 24,
                'taskRoleArn': 'test-role', 'containerDefinitions': [
                    {'name': 'tosunsaeng-learning-core-test', 'image': 'unchanged',
                     'secrets': [{'name': 'MONGODB_URI', 'valueFrom': 'unchanged-secret'}],
                     'environment': [{'name': 'AWS_S3_BUCKET_NAME', 'value': 'exam-bucket'},
                                     {'name': 'SPRING_APPLICATION_JSON', 'value': '{"app":{"notifications":{}}}'}]},
                    {'name': 'sidecar', 'image': 'unchanged-sidecar'}]}

    def test_preserves_existing_services_secrets_and_input(self):
        task = self.task()
        before = copy.deepcopy(task)
        result = prepare(task, URL)
        self.assertEqual(task, before)
        self.assertNotIn('revision', result)
        self.assertEqual(result['taskRoleArn'], task['taskRoleArn'])
        container = result['containerDefinitions'][0]
        self.assertEqual(container['image'], 'unchanged')
        self.assertEqual(container['secrets'], task['containerDefinitions'][0]['secrets'])
        self.assertEqual(result['containerDefinitions'][1], task['containerDefinitions'][1])
        env = {e['name']: e['value'] for e in container['environment']}
        self.assertEqual(env['AWS_S3_BUCKET_NAME'], 'exam-bucket')
        self.assertEqual(env['ANSWER_LEARNING_ENABLED'], 'true')
        self.assertEqual(env['ANSWER_LEARNING_LAMBDA_URL'], URL)
        self.assertEqual(prepare(result, URL), result)

    def test_rejects_other_environment_or_endpoint(self):
        for family in ('tosunsaeng-learning-core', 'tosunsaeng-learning-core-prod'):
            task = self.task(); task['family'] = family
            with self.assertRaises(ValueError): prepare(task, URL)
        for url in ('http://example.com', URL + '?redirect=1', 'https://fixture.lambda-url.us-east-1.on.aws/'):
            with self.assertRaises(ValueError): prepare(self.task(), url)

    def test_rejects_shadowing_configuration(self):
        task = self.task()
        task['containerDefinitions'][0]['secrets'].append({'name': 'ANSWER_LEARNING_ENABLED', 'valueFrom': 'x'})
        with self.assertRaises(ValueError): prepare(task, URL)
        task = self.task()
        task['containerDefinitions'][0]['environment'][1]['value'] = '{"app":{"answer-learning":{"enabled":false}}}'
        with self.assertRaises(ValueError): prepare(task, URL)


if __name__ == '__main__':
    unittest.main()
