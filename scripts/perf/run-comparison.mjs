// Local-only load generator and controlled AI/S3 fixture. No real audio or provider calls.
import http from 'node:http';
import {spawn} from 'node:child_process';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {performance} from 'node:perf_hooks';
import {once} from 'node:events';

const smoke=process.argv.includes('--smoke');
const timing=process.argv.includes('--ai-timing');
const output=process.argv.find(x=>x.startsWith('--output='))?.slice(9) ?? `build/poc-comparison/${timing?'ai-timing-':''}${smoke?'smoke':'results'}.json`;
const delay=ms=>new Promise(r=>setTimeout(r,ms));
const quantile=(a,p)=>a.length?[...a].sort((x,y)=>x-y)[Math.ceil(a.length*p)-1]:null;
const stats=a=>({n:a.length,p50:quantile(a,.5),p95:quantile(a,.95),p99:quantile(a,.99),max:a.length?Math.max(...a):null});
let backendPort, child, state=new Map(), scenario='normal';
let callbackErrors=0, aiCalls=0, summaryCalls=0, earlySummary=0, activeCallbacks=0;
let responseMode='async', aiRunning=0, aiQueue=[], aiPeak=0;
const aiWorkers=64;
function enqueue(job){aiQueue.push(job);pump();}
function pump(){while(aiRunning<aiWorkers&&aiQueue.length){const job=aiQueue.shift();aiRunning++;aiPeak=Math.max(aiPeak,aiRunning);
  Promise.resolve().then(job).catch(()=>{callbackErrors++;}).finally(()=>{aiRunning--;pump();});}}
const timers=new Set();
function later(fn,ms) {const t=setTimeout(async()=>{timers.delete(t);activeCallbacks++;try{await fn();}catch{callbackErrors++;}finally{activeCallbacks--;}},ms);timers.add(t);}
const part=q=>q===0?0:q<=2?1:q<=4?2:q<=7?3:q<=10?4:5;
async function request(path,method='GET',body=null,timeout=10000) {
  const start=performance.now();
  try {
    const r=await fetch(`http://127.0.0.1:${backendPort}${path}`,{method,
      headers:body?{'Content-Type':'application/json'}:undefined,body:body?JSON.stringify(body):undefined,
      signal:AbortSignal.timeout(timeout)});
    const text=await r.text(); let data;try{data=JSON.parse(text);}catch{}
    return {ok:r.ok&&data?.isSuccess!==false,status:r.status,ms:performance.now()-start,data};
  }catch(e){return {ok:false,status:e.name,ms:performance.now()-start};}
}
const fixture=http.createServer(async(req,res)=>{
  if(req.url==='/audio'){res.writeHead(200,{'Content-Type':'application/octet-stream'});res.end(Buffer.alloc(4096));return;}
  if(req.url!=='/evaluations'){res.writeHead(404);res.end();return;}
  const chunks=[];for await(const chunk of req)chunks.push(chunk);
  const raw=Buffer.concat(chunks).toString();
  const field=name=>new RegExp(`name="${name}"[^]*?\r\n\r\n([^\r\n]*)`).exec(raw)?.[1];
  let id,q,generation;
  if((req.headers['content-type']??'').includes('application/json')){
    const body=JSON.parse(raw);id=body.user_id;q=Number(body.question_number);generation=body.generation_attempt;
  }else{id=field('user_id');q=Number(field('question_number'));}
  const s=state.get(id);
  if(!s||!Number.isInteger(q)){res.writeHead(400);res.end();return;}
  if(q===0){summaryCalls++;if(s.answers.size<11)earlySummary++;}
  else aiCalls++;
  if(timing){
    // Verified historical ordering: processing + synchronous callback before HTTP response.
    // Matched async control: same bounded workers/processing/callback, early HTTP acceptance only.
    const selected=responseMode;
    await delay(50);
    if(selected==='async'){res.writeHead(202,{'Content-Type':'application/json'});res.end('{}');}
    enqueue(async()=>{
      await delay(q===0?100:2000);
      const body={user_id:id,mock_exam_id:'mock_exam_001',question_number:q,part_number:part(q),retry_count:0};
      if(q===0)Object.assign(body,{suggested_total_score:150,part_feedback:{part1:'fixture'},summary:'fixture'});
      else Object.assign(body,{score:3,max_score:3});
      const callback=await request('/api/v1/exams/callback/feedback','POST',body);
      if(!callback.ok)callbackErrors++;
      else if(q===0)s.summaryAck=true;else s.answers.add(q);
      if(selected==='sync'){res.writeHead(callback.ok?200:500,{'Content-Type':'application/json'});res.end('{}');}
    });
    return;
  }
  // Both variants get identical acceptance and grading latency, no AI-side deduplication.
  await delay(50);res.writeHead(202,{'Content-Type':'application/json'});res.end('{}');
  const processing=q===0?100:scenario==='reordered'&&q===1?2500:500;
  later(async()=>{
    const body={user_id:id,mock_exam_id:'mock_exam_001',question_number:q,part_number:part(q),retry_count:0};
    if(q===0)Object.assign(body,{generation_attempt:generation,suggested_total_score:150,part_feedback:{part1:'fixture'},summary:'fixture'});
    else Object.assign(body,{score:3,max_score:3});
    const callback=await request('/api/v1/exams/callback/feedback','POST',body);
    if(!callback.ok)callbackErrors++;
    else if(q===0)s.summaryAck=true;else s.answers.add(q);
  },processing);
});
await new Promise(r=>fixture.listen(0,'127.0.0.1',r));
const aiPort=fixture.address().port;
const results=[];
async function start(mode){
  const cp=(await readFile(`build/poc-comparison/${mode}-classpath.txt`,'utf8')).trim();
  const java=(await readFile('build/poc-comparison/java-path.txt','utf8')).trim();
  child=spawn(java,['-Xms256m','-Xmx512m','-XX:ActiveProcessorCount=2','-Dapi.version=1.44','-cp',cp,'benchmark.ComparisonServer',mode,String(aiPort)],
    {env:{PATH:process.env.PATH,HOME:process.env.HOME,DOCKER_HOST:process.env.DOCKER_HOST},stdio:['ignore','pipe','pipe']});
  let log='';
  child.stderr.on('data',b=>{log+=b.toString();});
  await new Promise((resolve,reject)=>{
    const timer=setTimeout(()=>reject(new Error(`startup timeout\n${log.slice(-4000)}`)),90000);
    child.stdout.on('data',b=>{log+=b.toString();const m=/BENCH_READY (\d+)/.exec(log);if(m){backendPort=Number(m[1]);clearTimeout(timer);resolve();}});
    child.once('exit',code=>{clearTimeout(timer);reject(new Error(`server exited ${code}\n${log.slice(-6000)}`));});
  });
  console.log(`READY ${mode}`);
  return ()=>log;
}
async function stop(){
  if(child&&child.exitCode===null){const exited=once(child,'exit');child.kill('SIGTERM');await Promise.race([exited,delay(15000)]);
    if(child.exitCode===null&&child.signalCode===null){child.kill('SIGKILL');await exited;}}
  child=null;
}
async function run(mode,kind,rate,seconds,repeat){
  scenario=kind;state=new Map();callbackErrors=0;aiCalls=0;summaryCalls=0;earlySummary=0;aiPeak=0;
  const n=Math.max(1,Math.round(rate*seconds));
  const ids=Array.from({length:n},(_,i)=>`ex_bench_${mode}_${kind}_${repeat}_${rate}_${i}`);
  for(const id of ids)state.set(id,{answers:new Set(),summaryAck:false});
  const seed=await request('/__bench/seed','POST',ids);if(!seed.ok)throw new Error('seed failed');
  const submit=[],submitResponses=[],poll=[],lag=[],finished=[],unfinished=[],httpErrors={};let premature=0,failed=0;
  const begun=performance.now();
  async function exam(id,scheduled){
    await delay(Math.max(0,scheduled-performance.now()));
    const actual=performance.now();lag.push(Math.max(0,actual-scheduled));
    const pending=[];
    for(let q=1;q<=11;q++){
      pending.push(request(`/api/v1/exams/${id}/questions/${q}/submit`,'POST').then(r=>{
        submit.push(r.ms);submitResponses.push({ok:r.ok,status:r.status,ms:r.ms});if(!r.ok)httpErrors[r.status]=(httpErrors[r.status]??0)+1;
      }));
      if(kind==='duplicate')pending.push(request(`/api/v1/exams/${id}/questions/${q}/submit`,'POST').then(r=>{
        submit.push(r.ms);submitResponses.push({ok:r.ok,status:r.status,ms:r.ms});if(!r.ok)httpErrors[r.status]=(httpErrors[r.status]??0)+1;
      }));
      await delay(30); // Identical compressed submission schedule, not an actual 20-minute exam.
    }
    let wasPremature=false,lastStatus=null;
    while(performance.now()-actual<15000){
      const r=await request(`/api/v1/exams/${id}/status`);poll.push(r.ms);
      lastStatus=r.data?.result?.overallStatus??r.status;
      if(!r.ok)httpErrors[r.status]=(httpErrors[r.status]??0)+1;
      if(performance.now()-actual>=15000)break;
      if(r.data?.result?.overallStatus==='COMPLETED'){
        const s=state.get(id);
        // Allow callback response/network bookkeeping to settle, then check durable results via API.
        const summary=await request(`/api/v1/exams/${id}/summary`);
        const solved=summary.data?.result?.totalSolvedQuestions;
        if(solved!==11){if(!wasPremature){premature++;wasPremature=true;}}
        else if(s.answers.size===11&&s.summaryAck){finished.push({id,ms:performance.now()-scheduled,at:performance.now()-begun});await Promise.all(pending);return;}
      }
      await delay(200);
    }
    failed++;unfinished.push({id,lastStatus,observedMs:performance.now()-actual});await Promise.all(pending);
  }
  await Promise.all(ids.map((id,i)=>exam(id,begun+i*1000/rate)));
  const drainStarted=performance.now();
  while(timers.size||activeCallbacks||aiRunning||aiQueue.length){
    if(performance.now()-drainStarted>60000)throw new Error('AI fixture drain exceeded 60 seconds');
    await delay(50);
  }
  await delay(300);
  const elapsed=performance.now()-begun;
  const metrics=await request('/__bench/metrics');
  const busy=metrics.data?.busy??[];
  if(!busy.length||metrics.data?.samplerError)throw new Error(`thread sampler failed: ${metrics.data?.samplerError}`);
  const result={mode,kind,rate,seconds,repeat,...(timing?{responseMode,aiPeak}:{}),offeredExams:n,complete:finished.length,failed,premature,
    aiCalls,summaryCalls,earlySummary,callbackErrors,httpErrors,elapsedMs:elapsed,
    completePerMinuteIncludingDrain:finished.length*60000/elapsed,
    completeWithinOfferWindow:finished.filter(x=>x.at<=seconds*1000).length,
    submitMs:stats(submit),submitSuccessMs:stats(submitResponses.filter(r=>r.ok).map(r=>r.ms)),submitFailures:submitResponses.filter(r=>!r.ok).length,
    pollMs:stats(poll),completionMs:stats(finished.map(x=>x.ms)),generatorLagMs:stats(lag),
    busy:{...stats(busy),mean:busy.length?busy.reduce((a,b)=>a+b,0)/busy.length:null,atCapacitySamples:busy.filter(n=>n===20).length},
    raw:{submitMs:submit,submitResponses,pollMs:poll,finished,unfinished,busy,generatorLagMs:lag}};
  console.log(JSON.stringify({...result,raw:undefined}));
  return result;
}
try {
  const repetitions=smoke?1:3;
  for(let repeat=1;repeat<=repetitions;repeat++)for(const variant of timing?(repeat%2?['sync','async']:['async','sync']):(repeat%2?['web','app']:['app','web'])){
    const mode=timing?'web':variant;
    responseMode=timing?variant:'async';
    const log=await start(mode);
    try{
      await run(mode,'warmup',timing?.25:1,timing?8:(smoke?1:4),repeat);
      const matrix=timing?(smoke?[['timing-low',.25,4],['timing-loaded',1,3]]:[['timing-low',.25,8],['timing-loaded',1,6]]):smoke?[['normal',1,2],['duplicate',1,2],['reordered',1,1]]:
        [['normal',1,8],['normal',5,8],['normal',15,8],['duplicate',5,8],['reordered',1,4]];
      for(const [kind,rate,seconds]of matrix)results.push(await run(mode,kind,rate,seconds,repeat));
    } finally {
      await writeFile(`build/poc-comparison/${timing?'timing-':''}${smoke?'smoke':'run'}-${variant}-${repeat}.log`,log());await stop();
    }
  }
} finally {
  await stop();for(const t of timers)clearTimeout(t);fixture.closeAllConnections();fixture.close();
  await mkdir(output.slice(0,output.lastIndexOf('/')),{recursive:true});
  await writeFile(output,JSON.stringify({date:new Date().toISOString(),smoke,timing,
    protocol:{heapMiB:512,activeProcessorCount:2,tomcatThreads:20,mongoCPU:1,mongoMiB:512,redisCPU:.5,redisMiB:128,
      aiAcceptanceMs:50,questionCallbackMs:500,summaryCallbackMs:100,reorderedQ1Ms:2500,
      pollMs:200,questionSubmitGapMs:30,examDeadlineMs:15000,
      ...(timing?{questionCallbackMs:2000,aiWorkers,callbackBeforeSyncResponse:true,callbackTimeoutMs:10000,backendReadTimeoutMs:5000,
        historicalSource:'web-ai fd43a983c9b9 -> 1134e1c5f454',historicalDeploymentReproduced:false}: {})},results},null,2));
}
