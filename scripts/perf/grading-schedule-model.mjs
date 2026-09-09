// Explicit experiment assumptions, not a recording of the deployed mobile app.
export const questionIntervalsSeconds = [90,90,75,75,18,18,33,63,18,33,105];
export const gradingMs = [600,1000,1800,1300,800,1500,1100,2000,900,1600,2200];
export const scale = 20;
export const answerOffsetsMs = questionIntervalsSeconds.map((_,i)=>questionIntervalsSeconds.slice(0,i+1).reduce((a,b)=>a+b,0)*1000/scale);
export const sleep = ms=>new Promise(resolve=>setTimeout(resolve,ms));
export const stats = values=>{
  const v=[...values].sort((a,b)=>a-b);
  const q=p=>v.length?v[Math.ceil(v.length*p)-1]:null;
  return {n:v.length,p50:q(.5),p95:q(.95),max:v.length?v.at(-1):null};
};
export class Pool {
  constructor(limit){
    if(!Number.isInteger(limit)||limit<1)throw new Error('positive worker limit required');
    this.limit=limit;this.active=0;this.pending=[];this.peakActive=0;this.peakWaiting=0;
  }
  add(job){this.pending.push(job);this.pump();this.peakWaiting=Math.max(this.peakWaiting,this.pending.length);}
  pump(){while(this.active<this.limit&&this.pending.length){
    const job=this.pending.shift();this.active++;this.peakActive=Math.max(this.peakActive,this.active);
    Promise.resolve().then(job).finally(()=>{this.active--;this.pump();});
  }}
}
export class ReleaseGate {
  constructor(mode,release){
    if(!['per-question','after-eleven'].includes(mode))throw new Error('unknown release mode');
    this.mode=mode;this.release=release;this.seen=new Set();this.held=[];this.ended=false;
  }
  accept(question,job){
    if(!Number.isInteger(question)||question<1||question>11||this.seen.has(question))throw new Error('invalid/duplicate fixture question');
    this.seen.add(question);
    if(this.mode==='per-question')this.release(job);else this.held.push({question,job});
    this.flush();
  }
  end(){this.ended=true;this.flush();}
  flush(){if(this.mode==='after-eleven'&&this.ended&&this.seen.size===11){
    for(const item of this.held.sort((a,b)=>a.question-b.question))this.release(item.job);
    this.held=[];
  }}
}
