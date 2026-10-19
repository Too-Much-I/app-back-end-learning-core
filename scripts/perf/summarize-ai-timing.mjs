import {readFile,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
const path=process.argv[2]??'docs/codex/measurements/ai-sync-async-2026-09-08.json';
const data=JSON.parse(await readFile(path,'utf8'));
assert.equal(data.timing,true);assert.equal(data.smoke,false);assert.equal(data.results.length,12);
const median=a=>a.length?[...a].sort((x,y)=>x-y)[Math.floor(a.length/2)]:null;
const range=a=>{a=a.filter(Number.isFinite);return a.length?{median:median(a),min:Math.min(...a),max:Math.max(...a)}:null;};
const groups=new Map();
for(const r of data.results){
  assert.equal(r.mode,'web');assert.equal(r.complete+r.failed,r.offeredExams);
  assert.equal(r.raw.submitResponses.length,r.offeredExams*11);
  assert.equal(r.submitFailures,r.raw.submitResponses.filter(s=>!s.ok).length);
  assert.equal(r.raw.unfinished.length,r.failed);assert.ok(r.raw.busy.length>0);
  assert.ok(r.raw.busy.every(n=>n>=0&&n<=20));assert.ok(r.aiPeak<=64);
  const key=`${r.kind}/${r.responseMode}`;if(!groups.has(key))groups.set(key,[]);groups.get(key).push(r);
}
const summary=[];
for(const[key,rows]of groups){
  assert.equal(rows.length,3);
  const sum=k=>rows.reduce((s,r)=>s+r[k],0);
  summary.push({key,exams:sum('offeredExams'),completedWithinObservation:sum('complete'),notCompletedWithinObservation:sum('failed'),
    submissions:rows.reduce((s,r)=>s+r.submitMs.n,0),submissionFailures:sum('submitFailures'),
    submissionFailurePercent:100*sum('submitFailures')/rows.reduce((s,r)=>s+r.submitMs.n,0),
    callbackErrors:sum('callbackErrors'),aiQuestionRequests:sum('aiCalls'),aiSummaryRequests:sum('summaryCalls'),
    submitP95Ms:range(rows.map(r=>r.submitMs.p95)),successfulSubmitP95Ms:range(rows.map(r=>r.submitSuccessMs.p95)),
    pollP95Ms:range(rows.map(r=>r.pollMs.p95)),completionP95Ms:range(rows.map(r=>r.completionMs.p95)),
    busyMean:range(rows.map(r=>r.busy.mean)),busyP95:range(rows.map(r=>r.busy.p95)),
    peakBusy:Math.max(...rows.map(r=>r.busy.max)),
    capacitySamplePercent:range(rows.map(r=>100*r.busy.atCapacitySamples/r.busy.n)),
    maxGeneratorLagMs:Math.max(...rows.map(r=>r.generatorLagMs.max)),
    byRepeat:rows.map(r=>({repeat:r.repeat,complete:r.complete,failed:r.failed,submitFailures:r.submitFailures,httpErrors:r.httpErrors}))});
}
const hashes={};for(const p of ['scripts/perf/run-comparison.mjs','scripts/perf/java/ComparisonServer.java'])hashes[p]=createHash('sha256').update(await readFile(p)).digest('hex');
const result={source:path,interpretation:'Historical-source-backed controlled HTTP ordering simulation, not a historical deployment benchmark. Fixed web backend and mock AI computation.',
  backend:'web-back-end 89c8f9d23761678e1a03328550d0f45f4d1a680f, same code in both arms',
  historicalSync:'https://github.com/Too-Much-I/web-ai/blob/fd43a983c9b9976e9320fa426a81d5ee0b9c0cea/app/api/server.py',
  historicalQueue:'https://github.com/Too-Much-I/web-ai/commit/1134e1c5f454b2aaa2a3da11e2113bc37c8598e2',hashes,summary};
await writeFile(path.replace(/\.json$/,'.summary.json'),JSON.stringify(result,null,2));
console.log(JSON.stringify(result,null,2));
