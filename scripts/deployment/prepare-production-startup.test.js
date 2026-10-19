const { test } = require('node:test');
const assert = require('node:assert/strict');
const { prepareProductionStartup } = require('./prepare-production-startup.cjs');
const candidate = (command = 'export EXAMPLE=test; exec java -jar /app/app.jar\n') => ({
  name: 'tosunsaeng-learning-core-test', entryPoint: ['/bin/sh', '-c'], command: [command],
  environment: [{ name: 'TEST_ONLY', value: 'fake-private-value' }], stopTimeout: 30,
});
test('preserves prefix, metadata, input and trailing whitespace; only replaces final exec', () => {
  const input = candidate(); const snapshot = structuredClone(input);
  const result = prepareProductionStartup(input);
  assert.deepEqual(input, snapshot);
  assert.deepEqual(result, { ...snapshot, command: ['export EXAMPLE=test; exec sh /app/access-events-entrypoint.sh java -jar /app/app.jar\n'] });
});
for (const [name, mutate] of [
  ['wrong container', x => { x.name = 'other'; }],
  ['different shell', x => { x.entryPoint = ['bash', '-c']; }],
  ['extra argument', x => { x.command.push('extra'); }],
  ['different JVM args', x => { x.command = ['exec java -Xmx1g -jar /app/app.jar']; }],
  ['duplicate tail', x => { x.command = ['exec java -jar /app/app.jar; exec java -jar /app/app.jar']; }],
  ['already prepared', x => { x.command = ['exec sh /app/access-events-entrypoint.sh java -jar /app/app.jar']; }],
  ['extra command after exec', x => { x.command = ['exec java -jar /app/app.jar; true']; }],
]) test(`rejects ${name} without echoing input`, () => {
  const input = candidate(); mutate(input);
  assert.throws(() => prepareProductionStartup(input), {
    message: 'Production startup contract does not match reviewed shape',
  });
});
