// Isolated real app HTTP/repositories + artificial grading start gate. No production configuration.
import http from 'node:http';
import {spawn} from 'node:child_process';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {performance} from 'node:perf_hooks';
import {once} from 'node:events';
import assert from 'node:assert/strict';
import {Pool,ReleaseGate,answerOffsetsMs,questionIntervalsSeconds,gradingMs,scale,sleep,stats} from './grading-schedule-model.mjs';

const smoke=process.argv.includes('--smoke');
const output=process.argv.find(x=>x.startsWith('--output='))?.slice(9)??`build/poc-comparison/grading-schedule-${smoke?'smoke':'results'}.json`;
const acceptanceMs=50,summaryMs=300,pollMs=200,deadlineMs=60000;
let child,backendPort,current,log='',activeHttp=0;
const results=[];
const at=()=>performance.now()-current.started;
async function request(path,method='GET',body=null){
  const start=performance.now();
  try{
    const response=await fetch(`http://127.0.0.1:${backendPort}${path}`,{
      method,headers:body?{'Content-Type':'application/json'}:undefined,
      body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(10000)});
    const text=await response.text();let data;try{data=JSON.parse(text);}catch{}
    return {ok:response.ok&&data?.isSuccess!==false,status:response.status,ms:performance.now()-start,data};
  }catch(e){return {ok:false,status:e.name,ms:performance.now()-start};}
}
function enqueue(job){job.eligibleAtMs=at();current.pool.add(async()=>{
  try{
    job.startAtMs=at();
    await sleep(job.question===0?summaryMs:gradingMs[job.question-1]);
    job.computeEndAtMs=at();
    const q=job.question;
    const body={user_id:job.id,mock_exam_id:'mock_exam_001',question_number:q,
      part_number:q===0?0:q<=2?1:q<=4?2:q<=7?3:q<=10?4:5,retry_count:0};
    if(q===0)Object.assign(body,{generation_attempt:job.generation,suggested_total_score:150,part_feedback:{part1:'fixture'},summary:'fixture'});
    else Object.assign(body,{score:3,max_score:3});
    const callback=await request('/api/v1/exams/callback/feedback','POST',body);
    job.callback={ok:callback.ok,status:callback.status,ms:callback.ms};job.callbackAckAtMs=at();
    if(!callback.ok)current.errors.push({type:'callback',question:q,status:callback.status});
    else if(q===0)current.exams.get(job.id).summaryAck=true;
    else current.exams.get(job.id).answers.add(q);
  }catch(error){current.errors.push({type:'job',message:error.message});}
});}
const fixture=http.createServer((req,res)=>{
  activeHttp++;
  (async()=>{
    if(req.url==='/audio'){res.writeHead(200,{'Content-Type':'application/octet-stream'});res.end(Buffer.alloc(4096));return;}
    if(req.url!=='/evaluations'){res.writeHead(404);res.end();return;}
    const chunks=[];for await(const chunk of req)chunks.push(chunk);
    const raw=Buffer.concat(chunks).toString();
    const field=name=>new RegExp(`name="${name}"[^]*?\r\n\r\n([^\r\n]*)`).exec(raw)?.[1];
    let id,q,generation;
    if((req.headers['content-type']??'').includes('application/json')){
      const body=JSON.parse(raw);id=body.user_id;q=Number(body.question_number);generation=body.generation_attempt;
    }else{id=field('user_id');q=Number(field('question_number'));}
    const exam=current?.exams.get(id);
    if(!exam||!Number.isInteger(q)||q<0||q>11){res.writeHead(400);res.end();return;}
    const job={id,question:q,generation,receivedAtMs:at()};current.jobs.push(job);
    await sleep(acceptanceMs);job.acceptedAtMs=at();res.writeHead(202,{'Content-Type':'application/json'});res.end('{}');
    if(q===0)enqueue(job);else exam.gate.accept(q,job);
  })().catch(error=>{
    current?.errors.push({type:'fixture',message:error.message});if(!res.headersSent)res.writeHead(500);res.end();
  }).finally(()=>activeHttp--);
});
await new Promise(resolve=>fixture.listen(0,'127.0.0.1',resolve));
async function start(){
  log='';const cp=(await readFile('build/poc-comparison/app-classpath.txt','utf8')).trim();
  const java=(await readFile('build/poc-comparison/java-path.txt','utf8')).trim();
  child=spawn(java,['-Xms256m','-Xmx512m','-XX:ActiveProcessorCount=2','-Dapi.version=1.44','-cp',cp,'benchmark.ComparisonServer','app',String(fixture.address().port)],
    {env:{PATH:process.env.PATH,HOME:process.env.HOME,DOCKER_HOST:process.env.DOCKER_HOST},stdio:['ignore','pipe','pipe']});
  child.stderr.on('data',b=>{log+=b.toString();});
  await new Promise((resolve,reject)=>{
    const timer=setTimeout(()=>reject(new Error(`startup timeout: ${log.slice(-3000)}`)),60000);
    child.stdout.on('data',b=>{log+=b.toString();const match=/BENCH_READY (\d+)/.exec(log);if(match){backendPort=Number(match[1]);clearTimeout(timer);resolve();}});
    child.once('error',error=>{clearTimeout(timer);reject(error);});
    child.once('exit',code=>{clearTimeout(timer);reject(new Error(`startup exited ${code}: ${log.slice(-3000)}`));});
  });
}
async function stop(){
  if(child&&child.exitCode===null&&child.signalCode===null){
    const exited=once(child,'exit');child.kill('SIGTERM');await Promise.race([exited,sleep(15000)]);
    if(child.exitCode===null&&child.signalCode===null){child.kill('SIGKILL');await exited;}
  }
  child=null;
}
async function run(mode,config,repeat,warmup=false){
  current={mode,config,started:performance.now(),exams:new Map(),jobs:[],errors:[],pool:new Pool(config.workers)};
  const ids=Array.from({length:config.count},(_,i)=>`ex_bench_schedule_${repeat}_${mode}_${config.name}_${i}_${warmup?'warm':'main'}`);
  for(const id of ids)current.exams.set(id,{answers:new Set(),summaryAck:false,gate:new ReleaseGate(mode,enqueue)});
  assert.equal((await request('/__bench/seed','POST',ids)).ok,true,'fixture seed failed');
  current.started=performance.now();
  const samples=[],submits=[],polls=[],exams=[];
  const sampler=setInterval(()=>samples.push({atMs:at(),running:current.pool.active,waiting:current.pool.pending.length,
    held:[...current.exams.values()].reduce((sum,e)=>sum+e.gate.held.length,0)}),20);
  console.log(`START ${repeat} ${mode} ${config.name} workers=${config.workers} exams=${ids.length}`);
  try{
    await Promise.all(ids.map(async(id,index)=>{
      const offset=index*(warmup?0:500),start=current.started+offset;
      const offsets=warmup||smoke?Array.from({length:11},(_,i)=>(i+1)*60):answerOffsetsMs;
      const exam=current.exams.get(id),pending=[],ready=[];
      const count=config.dropAfter??11;
      let endAt,completeAt=null,lastStatus=null;
      for(let q=1;q<=count;q++){
        await sleep(Math.max(0,start+offsets[q-1]-performance.now()));
        const readyAt=at();ready.push({question:q,atMs:readyAt,lagMs:Math.max(0,performance.now()-start-offsets[q-1])});
        if(q===count){
          endAt=readyAt;
          if(!config.dropAfter)exam.gate.end();
        }
        const submittedAt=at();
        pending.push(request(`/api/v1/exams/${id}/questions/${q}/submit`,'POST').then(r=>{
          submits.push({id,question:q,submittedAtMs:submittedAt,returnedAtMs:at(),ok:r.ok,status:r.status,ms:r.ms});
          if(!r.ok)current.errors.push({type:'submit',status:r.status});
        }));
      }
      if(!config.dropAfter){
        while(at()-endAt<deadlineMs){
          const response=await request(`/api/v1/exams/${id}/status`);polls.push(response.ms);
          lastStatus=response.data?.result?.overallStatus??response.status;
          if(!response.ok)current.errors.push({type:'poll',status:response.status});
          if(lastStatus==='COMPLETED'){
            const summary=await request(`/api/v1/exams/${id}/summary`);
            if(summary.ok&&summary.data?.result?.totalSolvedQuestions===11&&exam.answers.size===11&&exam.summaryAck){completeAt=at();break;}
          }
          await sleep(pollMs);
        }
      }
      await Promise.all(pending);
      exams.push({id,answerReady:ready,endAtMs:endAt,completeAtMs:completeAt,
        afterEndMs:completeAt===null?null:completeAt-endAt,lastStatus,dropped:!!config.dropAfter,
        completedByEnd:current.jobs.filter(j=>j.id===id&&j.question>0&&j.callback?.ok&&j.callbackAckAtMs<=endAt).length});
    }));
    const drain=performance.now();
    while(current.pool.active||current.pool.pending.length||activeHttp){
      if(performance.now()-drain>60000)throw new Error('drain deadline exceeded');await sleep(25);
    }
    const metrics=await request('/__bench/metrics');
    assert.ok(metrics.data?.busy?.length&&!metrics.data.samplerError,'Tomcat samples missing');
    const jobs=current.jobs;
    for(const e of exams){
      const own=jobs.filter(j=>j.id===e.id&&j.question>0);
      const finalSubmit=submits.find(s=>s.id===e.id&&s.question===11);
      e.finalSubmitAckAtMs=finalSubmit?.ok?finalSubmit.returnedAtMs:null;
      e.afterFinalSubmitAckMs=e.completeAtMs!==null&&e.finalSubmitAckAtMs!==null?e.completeAtMs-e.finalSubmitAckAtMs:null;
      e.questionRequests=own.length;e.started=own.filter(j=>j.startAtMs!==undefined).length;
      e.completedAfterDrop=e.dropped?own.filter(j=>j.callback?.ok&&j.callbackAckAtMs>e.endAtMs).length:0;
      e.startedByDrop=e.dropped?own.filter(j=>j.startAtMs<=e.endAtMs).length:0;
    }
    const result={repeat,mode,config,smoke,exams,errors:current.errors,aiQuestionRequests:jobs.filter(j=>j.question>0).length,
      aiSummaryRequests:jobs.filter(j=>j.question===0).length,modelQuestionStarts:jobs.filter(j=>j.question>0&&j.startAtMs!==undefined).length,
      heldNotExecuted:[...current.exams.values()].reduce((n,e)=>n+e.gate.held.length,0),
      complete:exams.filter(e=>e.completeAtMs!==null&&e.afterEndMs<deadlineMs).length,
      afterEndMs:stats(exams.map(e=>e.afterEndMs).filter(Number.isFinite)),submitMs:stats(submits.map(s=>s.ms)),pollMs:stats(polls),
      afterFinalSubmitAckMs:stats(exams.map(e=>e.afterFinalSubmitAckMs).filter(Number.isFinite)),
      queueWaitMs:stats(jobs.filter(j=>j.startAtMs!==undefined).map(j=>j.startAtMs-j.eligibleAtMs)),
      aiPeakActive:current.pool.peakActive,aiPeakWaiting:current.pool.peakWaiting,
      generatorLagMs:stats(exams.flatMap(e=>e.answerReady.map(a=>a.lagMs))),
      raw:{jobs,samples,submits,tomcatBusy:metrics.data.busy}};
    console.log(JSON.stringify({...result,exams:undefined,raw:undefined}));return result;
  }finally{clearInterval(sampler);}
}
const sourcePaths=['scripts/perf/run-grading-schedule.mjs','scripts/perf/grading-schedule-model.mjs','scripts/perf/java/ComparisonServer.java',
  'src/main/java/web/tosunsaeng/domain/exams/application/ExamGradingService.java','src/main/java/web/tosunsaeng/domain/exams/application/ExamServiceImpl.java'];
const hashes={};for(const path of sourcePaths)hashes[path]=createHash('sha256').update(await readFile(path)).digest('hex');
let succeeded=false;
try{
  for(let repeat=1;repeat<=(smoke?1:3);repeat++)for(const mode of repeat%2?['per-question','after-eleven']:['after-eleven','per-question']){
    try{
      await start();
      await run(mode,{name:'warmup',count:2,workers:4},repeat,true);
      const matrix=smoke?[{name:'cohort',count:2,workers:4},{name:'drop-five',count:1,workers:4,dropAfter:5}]:[
        {name:'single-four',count:1,workers:4},{name:'cohort-six',count:6,workers:4},
        {name:'single-sixteen',count:1,workers:16},{name:'drop-five',count:2,workers:4,dropAfter:5}];
      for(const config of matrix)results.push(await run(mode,config,repeat));
    }finally{await writeFile(`build/poc-comparison/schedule-${smoke?'smoke':'main'}-${mode}-${repeat}.log`,log);await stop();}
  }
  succeeded=true;
}finally{
  await stop();fixture.closeAllConnections();fixture.close();
  await mkdir(output.slice(0,output.lastIndexOf('/')),{recursive:true});
  await writeFile(output,JSON.stringify({date:new Date().toISOString(),succeeded,smoke,hashes,
    protocol:{backend:'same app code both arms; current working tree',gradingStartGate:'AI fixture; submit/upload unchanged',
      scale,questionIntervalsSeconds,answerOffsetsMs,gradingMs,acceptanceMs,summaryMs,pollMs,deadlineMs,
      heapMiB:512,activeProcessorCount:2,tomcatThreads:20,cohortOffsetMs:500,
      note:'Compressed modeled timeline. HTTP/DB/polling overhead unscaled; never multiply measured time into production latency.'},results},null,2),{flag:'wx'});
}
