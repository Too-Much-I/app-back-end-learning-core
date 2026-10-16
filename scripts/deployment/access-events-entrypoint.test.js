const { test } = require('node:test');
const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const { rmSync, rmdirSync } = require('node:fs');
const path = require('node:path');
const script = path.join(__dirname, 'access-events-entrypoint.sh');
const fixture = Buffer.from('test-only-keystore-bytes').toString('base64');
function run(extra, code = 'console.log("CHILD_STARTED")') {
  const env = { ...process.env };
  for (const k of Object.keys(env)) if (k.startsWith('BILLING_ACCESS_EVENTS_')) delete env[k];
  return spawnSync('sh', [script, process.execPath, '-e', code], {
    env: { ...env, ...extra }, encoding: 'utf8', timeout: 5000,
  });
}
const enabled = {
  BILLING_ACCESS_EVENTS_RECEIVER_ENABLED: 'true',
  BILLING_ACCESS_EVENTS_TLS_PKCS12_BASE64: fixture,
  BILLING_ACCESS_EVENTS_TLS_KEY_STORE_PASSWORD: 'test-only-password',
  BILLING_ACCESS_EVENTS_TLS_KEY_ALIAS: 'test-only-alias',
};
for (const value of [undefined, 'false']) {
  test(`OFF (${value}) ignores malformed material and strips encoded secret`, () => {
    const result = run({ ...(value ? { BILLING_ACCESS_EVENTS_RECEIVER_ENABLED: value } : {}),
      BILLING_ACCESS_EVENTS_TLS_PKCS12_BASE64: '!invalid!' },
    'console.log(process.env.BILLING_ACCESS_EVENTS_TLS_PKCS12_BASE64 === undefined ? "SAFE" : "LEAK")');
    assert.equal(result.status, 0); assert.equal(result.stdout.trim(), 'SAFE');
    assert.equal(result.stderr, '');
  });
}
test('ON produces exact bytes with private file/directory permissions and removes base64 env', () => {
  const result = run(enabled, `const fs=require('fs'),p=require('path');
    const f=process.env.BILLING_ACCESS_EVENTS_TLS_KEY_STORE;
    console.log(JSON.stringify({f,mode:fs.statSync(f).mode&511,dir:fs.statSync(p.dirname(f)).mode&511,
      exact:fs.readFileSync(f,'utf8')==='test-only-keystore-bytes',
      stripped:process.env.BILLING_ACCESS_EVENTS_TLS_PKCS12_BASE64===undefined}));`);
  assert.equal(result.status, 0, result.stderr);
  const o = JSON.parse(result.stdout);
  assert.match(o.f, /^\/tmp\/lc-access-events\.[A-Za-z0-9]+\/receiver\.p12$/);
  try { assert.equal(o.mode, 384); assert.equal(o.dir, 448); assert.ok(o.exact && o.stripped); }
  finally { rmSync(o.f); rmdirSync(path.dirname(o.f)); }
});
for (const [label, changes] of [
  ['invalid base64', { BILLING_ACCESS_EVENTS_TLS_PKCS12_BASE64: '!bad-value!' }],
  ['missing password', { BILLING_ACCESS_EVENTS_TLS_KEY_STORE_PASSWORD: '' }],
  ['missing alias', { BILLING_ACCESS_EVENTS_TLS_KEY_ALIAS: '' }],
  ['ambiguous file and base64', { BILLING_ACCESS_EVENTS_TLS_KEY_STORE: '/tmp/other.p12' }],
  ['invalid flag', { BILLING_ACCESS_EVENTS_RECEIVER_ENABLED: 'unexpected' }],
  ['missing material', { BILLING_ACCESS_EVENTS_TLS_PKCS12_BASE64: '' }],
  ['empty decoded material', { BILLING_ACCESS_EVENTS_TLS_PKCS12_BASE64: '\n' }],
  ['oversized material', { BILLING_ACCESS_EVENTS_TLS_PKCS12_BASE64: 'A'.repeat(65537) }],
  ['relative mounted path', { BILLING_ACCESS_EVENTS_TLS_PKCS12_BASE64: '', BILLING_ACCESS_EVENTS_TLS_KEY_STORE: 'relative.p12' }],
]) {
  test(`fail closed: ${label}`, () => {
    const r = run({ ...enabled, ...changes });
    assert.notEqual(r.status, 0); assert.equal(r.stdout, '');
    assert.equal(r.stderr.trim(), 'LC access TLS material preparation failed');
  });
}
test('existing mounted file path is passed to Java validation without decoding', () => {
  const r = run({ ...enabled, BILLING_ACCESS_EVENTS_TLS_PKCS12_BASE64: '',
    BILLING_ACCESS_EVENTS_TLS_KEY_STORE: '/mounted/receiver.p12' });
  assert.equal(r.status, 0); assert.equal(r.stdout.trim(), 'CHILD_STARTED');
});
test('child exit code is preserved', () => {
  const r = run({}, 'process.exit(23)'); assert.equal(r.status, 23);
});
