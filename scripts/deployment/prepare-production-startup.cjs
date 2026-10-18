'use strict';

// Pure local preparation only: no AWS calls, no input/output logging.
const before = 'exec java -jar /app/app.jar';
const after = 'exec sh /app/access-events-entrypoint.sh java -jar /app/app.jar';
function prepareProductionStartup(container) {
  const fail = () => { throw new Error('Production startup contract does not match reviewed shape'); };
  if (!container || container.name !== 'tosunsaeng-learning-core-test'
      || JSON.stringify(container.entryPoint) !== JSON.stringify(['/bin/sh', '-c'])
      || !Array.isArray(container.command) || container.command.length !== 1
      || typeof container.command[0] !== 'string') fail();
  const original = container.command[0];
  const script = original.trimEnd();
  if (script.includes('/app/access-events-entrypoint.sh')) fail();
  if (!script.endsWith(before) || script.split(before).length !== 2) fail();
  // Keep the reviewed prefix and its quoting byte-for-byte; do not export secrets.
  const command = script.slice(0, -before.length) + after + original.slice(script.length);
  return { ...container, command: [command] };
}
module.exports = { prepareProductionStartup };
