import {test} from 'node:test';
import assert from 'node:assert/strict';
import {Pool,ReleaseGate,answerOffsetsMs,gradingMs,stats} from './grading-schedule-model.mjs';
test('explicit scaled timeline and identical per-question processing',()=>{
  assert.equal(answerOffsetsMs.length,11);assert.equal(answerOffsetsMs.at(-1),30900);
  assert.ok(answerOffsetsMs.every((t,i)=>i===0||t>answerOffsetsMs[i-1]));
  assert.equal(gradingMs.length,11);assert.equal(gradingMs.reduce((a,b)=>a+b),14800);
});
test('per-question release does not wait for exam end',()=>{
  const released=[];const gate=new ReleaseGate('per-question',x=>released.push(x));
  for(let q=1;q<=5;q++)gate.accept(q,q);
  assert.deepEqual(released,[1,2,3,4,5]);assert.equal(gate.held.length,0);
});
test('batch waits for both end and all eleven accepted, releases exactly once',()=>{
  const released=[];const gate=new ReleaseGate('after-eleven',x=>released.push(x));
  for(let q=10;q>=1;q--)gate.accept(q,q);
  gate.end();assert.equal(released.length,0);gate.accept(11,11);gate.end();
  assert.deepEqual(released,Array.from({length:11},(_,i)=>i+1));
  assert.throws(()=>gate.accept(11,11));
});
test('eleven ready without end and dropped five remain held',()=>{
  for(const count of [5,11]){
    const released=[];const gate=new ReleaseGate('after-eleven',x=>released.push(x));
    for(let q=1;q<=count;q++)gate.accept(q,q);
    assert.equal(released.length,0);
    if(count===5){gate.end();assert.equal(released.length,0);}
  }
});
test('FIFO pool bounds concurrency and drains',async()=>{
  const pool=new Pool(2);const started=[];const finish=[];
  const done=Array.from({length:5},(_,i)=>new Promise(resolve=>pool.add(async()=>{
    started.push(i);await new Promise(r=>finish.push(r));resolve();
  })));
  await Promise.resolve();assert.deepEqual(started,[0,1]);assert.equal(pool.peakWaiting,3);
  for(let i=0;i<5;i++){
    while(!finish[i])await new Promise(r=>setImmediate(r));finish[i]();
  }
  await Promise.all(done);await new Promise(r=>setImmediate(r));
  assert.deepEqual(started,[0,1,2,3,4]);assert.equal(pool.peakActive,2);assert.equal(pool.active,0);
});
test('nearest rank statistics and empty samples',()=>{
  assert.equal(stats([]).p95,null);assert.equal(stats([3,1,2]).p50,2);
  assert.equal(stats(Array.from({length:100},(_,i)=>i+1)).p95,95);
});
