'use strict';

// Read-only test inventory. Credentials stay in memory/child environment, never argv or output.
const { spawnSync } = require('node:child_process');
const path = require('node:path');
const ACCOUNT = '889384901776';
const HOST = 'cluster0.lkpybbj.mongodb.net';
const DATABASE = 'to-teacher-learning-core-test';
const SECRET = 'arn:aws:secretsmanager:ap-northeast-2:889384901776:secret:tosunsaeng/test/learning-core/mongodb-NEcjAa';

function validateUri(uri) {
  if (typeof uri !== 'string' || uri.length > 8192 || /[\r\n\0]/.test(uri)) throw Error('INVALID_TEST_URI');
  let parsed;
  try { parsed = new URL(uri); } catch { throw Error('INVALID_TEST_URI'); }
  if (parsed.protocol !== 'mongodb+srv:' || parsed.hostname !== HOST || parsed.port
      || !parsed.username || !parsed.password
      || !['', '/', `/${DATABASE}`].includes(parsed.pathname)) throw Error('WRONG_TEST_TARGET');
  for (const [key, value] of parsed.searchParams) {
    if ((['tls', 'ssl'].includes(key.toLowerCase()) && value.toLowerCase() !== 'true')
        || ['tlsinsecure', 'tlsallowinvalidcertificates', 'tlsallowinvalidhostnames'].includes(key.toLowerCase()))
      throw Error('UNSAFE_TLS_OPTIONS');
  }
  return uri;
}

function run(exec = spawnSync, env = process.env) {
  function aws(args, stage) {
    const result = exec('aws', [...args, '--region', 'ap-northeast-2', '--output', 'json', '--no-cli-pager'],
      { encoding: 'utf8', timeout: 20000, maxBuffer: 128 * 1024, env });
    if (result.status !== 0 || result.error) throw Error(stage);
    try { return JSON.parse(result.stdout); } catch { throw Error(stage); }
  }
  const identity = aws(['sts', 'get-caller-identity'], 'AWS_AUTH_UNAVAILABLE');
  if (identity.Account !== ACCOUNT) throw Error('WRONG_AWS_ACCOUNT');
  const secret = aws(['secretsmanager', 'get-secret-value', '--secret-id', SECRET], 'TEST_SECRET_UNAVAILABLE');
  let payload;
  try { payload = JSON.parse(secret.SecretString); } catch { throw Error('INVALID_TEST_SECRET'); }
  const uri = validateUri(payload.MONGODB_URI);
  const childEnv = { ...env, LC_TEST_INVENTORY_URI: uri, MONGOSH_DISABLE_TELEMETRY: '1' };
  const result = exec('mongosh', ['--nodb', '--quiet', '--file', path.join(__dirname, 'inspect-paid-test-mongo.js')],
    { encoding: 'utf8', timeout: 120000, maxBuffer: 1024 * 1024, env: childEnv });
  delete childEnv.LC_TEST_INVENTORY_URI;
  if (result.status !== 0 || result.error) throw Error('TEST_INVENTORY_FAILED');
  let report;
  try { report = JSON.parse(result.stdout.trim()); } catch { throw Error('INVALID_INVENTORY_OUTPUT'); }
  if (report.database !== DATABASE || report.readOnly !== true || !Array.isArray(report.collections)
      || !report.snapshotCounts) throw Error('INVALID_INVENTORY_OUTPUT');
  return report;
}

if (require.main === module) {
  try { console.log(JSON.stringify(run(), null, 2)); }
  catch (failure) {
    const allowed = new Set(['INVALID_TEST_URI', 'WRONG_TEST_TARGET', 'UNSAFE_TLS_OPTIONS', 'AWS_AUTH_UNAVAILABLE',
      'WRONG_AWS_ACCOUNT', 'TEST_SECRET_UNAVAILABLE', 'INVALID_TEST_SECRET', 'TEST_INVENTORY_FAILED', 'INVALID_INVENTORY_OUTPUT']);
    console.error(allowed.has(failure.message) ? failure.message : 'TEST_INVENTORY_FAILED');
    process.exitCode = 1;
  }
}
module.exports = { validateUri, run, HOST, DATABASE, ACCOUNT };
