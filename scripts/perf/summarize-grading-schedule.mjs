import {readFile,writeFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
import {stats,answerOffsetsMs,gradingMs} from './grading-schedule-model.mjs';
const path=process.argv[2];assert.ok(path,'provide raw JSON path');
const data=JSON.parse(await readFile(path,'utf8'));
assert.equal(data.succeeded,true);assert.equal(data.smoke,false);assert.equal(data.results.length,24);
assert.deepEqual(data.protocol.answerOffsetsMs,answerOffsetsMs);assert.deepEqual(data.protocol.gradingMs,gradingMs);
const groups=new Map();
for(const r of data.results){
  const {count,dropAfter,workers,name}=r.config;
  assert.equal(r.errors.length,0,JSON.stringify(r.errors));
  assert.equal(r.aiQuestionRequests,count*(dropAfter??11));assert.equal(r.aiSummaryRequests,dropAfter?0:count);
  assert.equal(r.raw.submits.length,r.aiQuestionRequests);assert.ok(r.raw.submits.every(s=>s.ok));
  assert.equal(r.exams.length,count);assert.equal(r.complete,dropAfter?0:count);
  assert.ok(r.aiPeakActive<=workers);assert.ok(r.raw.samples.length>0);
  assert.ok(r.raw.tomcatBusy.length>0&&r.raw.tomcatBusy.every(n=>n>=0&&n<=20));
  assert.equal(r.modelQuestionStarts,dropAfter&&r.mode==='after-eleven'?0:count*(dropAfter??11));
  assert.equal(r.heldNotExecuted,dropAfter&&r.mode==='after-eleven'?count*dropAfter:0);
  for(const e of r.exams){
    assert.equal(e.answerReady.length,dropAfter??11);
    const jobs=r.raw.jobs.filter(j=>j.id===e.id&&j.question>0);
    assert.equal(new Set(jobs.map(j=>j.question)).size,jobs.length);
    if(!dropAfter){assert.ok(e.afterEndMs>=0&&e.afterEndMs<data.protocol.deadlineMs);assert.ok(e.afterFinalSubmitAckMs>=0);}
    for(const job of jobs){
      if(job.startAtMs===undefined){assert.ok(dropAfter&&r.mode==='after-eleven');continue;}
      assert.ok(job.callback?.ok);assert.ok(job.startAtMs>=job.acceptedAtMs);
      assert.ok(job.startAtMs>=e.answerReady[job.question-1].atMs);
      // Native timers may fire fractionally early.
      assert.ok(job.computeEndAtMs-job.startAtMs>=gradingMs[job.question-1]-5);
      if(r.mode==='after-eleven'){
        assert.ok(job.startAtMs>=e.endAtMs);
        assert.ok(job.startAtMs>=Math.max(...jobs.map(j=>j.acceptedAtMs)));
      }
    }
    assert.equal(e.completedByEnd,jobs.filter(j=>j.callback?.ok&&j.callbackAckAtMs<=e.endAtMs).length);
  }
  const key=`${name}/${r.mode}`;if(!groups.has(key))groups.set(key,[]);groups.get(key).push(r);
}
const range=values=>({median:stats(values).p50,min:Math.min(...values),max:Math.max(...values)});
const summary=[];
for(const[key,rows]of groups){
  assert.equal(rows.length,3);assert.deepEqual(rows.map(r=>r.repeat).sort(),[1,2,3]);
  const exams=rows.flatMap(r=>r.exams),drop=!!rows[0].config.dropAfter;
  summary.push({key,exams:exams.length,complete:rows.reduce((n,r)=>n+r.complete,0),
    afterEndP50Ms:drop?null:range(rows.map(r=>r.afterEndMs.p50)),afterEndP95Ms:drop?null:range(rows.map(r=>r.afterEndMs.p95)),
    afterFinalSubmitAckP95Ms:drop?null:range(rows.map(r=>r.afterFinalSubmitAckMs.p95)),
    completedByEnd:stats(exams.map(e=>e.completedByEnd)),
    aiPeakActive:range(rows.map(r=>r.aiPeakActive)),aiPeakWaiting:range(rows.map(r=>r.aiPeakWaiting)),
    queueWaitP95Ms:rows[0].queueWaitMs.n?range(rows.map(r=>r.queueWaitMs.p95)):null,
    questionRequests:rows.reduce((n,r)=>n+r.aiQuestionRequests,0),summaryRequests:rows.reduce((n,r)=>n+r.aiSummaryRequests,0),
    questionStarts:rows.reduce((n,r)=>n+r.modelQuestionStarts,0),heldNotExecuted:rows.reduce((n,r)=>n+r.heldNotExecuted,0),
    startedByDrop:drop?exams.reduce((n,e)=>n+e.startedByDrop,0):null,
    completedAfterDrop:drop?exams.reduce((n,e)=>n+e.completedAfterDrop,0):null,
    errors:rows.reduce((n,r)=>n+r.errors.length,0),maxGeneratorLagMs:Math.max(...rows.map(r=>r.generatorLagMs.max)),
    byRepeat:rows.map(r=>({repeat:r.repeat,afterEndMs:r.afterEndMs,completedByEnd:r.exams.map(e=>e.completedByEnd),
      aiPeakWaiting:r.aiPeakWaiting,aiPeakActive:r.aiPeakActive}))});
}
const result={source:path,interpretation:'Per-run p50/p95 medians; scaled artificial timeline and mock grading, not production latency or cost.',summary};
await writeFile(path.replace(/\.json$/,'.summary.json'),JSON.stringify(result,null,2),{flag:'wx'});
console.log(JSON.stringify(result,null,2));
