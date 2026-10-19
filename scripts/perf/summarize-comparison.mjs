import {readFile,writeFile,readdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import assert from 'node:assert/strict';

const file=process.argv[2]??'docs/codex/measurements/poc-comparison-2026-09-08.json';
const data=JSON.parse(await readFile(file,'utf8'));
assert.equal(data.smoke,false);
assert.equal(data.results.length,30,'2 variants x 5 conditions x 3 repetitions');
const median=values=>[...values].sort((a,b)=>a-b)[Math.floor(values.length/2)];
const range=values=>({median:median(values),min:Math.min(...values),max:Math.max(...values)});
const groups=new Map();
for(const row of data.results){
  assert.equal(row.offeredExams,row.complete+row.failed);
  assert.equal(row.raw.finished.length,row.complete);
  assert.ok(row.raw.busy.length>0);
  assert.ok(row.raw.busy.every(n=>n>=0&&n<=20));
  assert.equal(row.submitMs.n,row.offeredExams*11*(row.kind==='duplicate'?2:1));
  const key=`${row.kind}/${row.rate}/${row.mode}`;
  if(!groups.has(key))groups.set(key,[]);
  groups.get(key).push(row);
}
const summary=[];
for(const [key,rows]of groups){
  assert.equal(rows.length,3);
  const sum=k=>rows.reduce((n,r)=>n+r[k],0);
  summary.push({key,offered:sum('offeredExams'),complete:sum('complete'),failed:sum('failed'),
    premature:sum('premature'),aiCalls:sum('aiCalls'),summaryCalls:sum('summaryCalls'),
    callbackErrors:sum('callbackErrors'),httpErrors:rows.reduce((n,r)=>n+Object.values(r.httpErrors).reduce((a,b)=>a+b,0),0),
    submitP95Ms:range(rows.map(r=>r.submitMs.p95)),completionP95Ms:range(rows.map(r=>r.completionMs.p95)),
    pollP95Ms:range(rows.map(r=>r.pollMs.p95)),busyMean:range(rows.map(r=>r.busy.mean)),busyP95:range(rows.map(r=>r.busy.p95)),
    observedCompletedPerMinuteIncludingDrain:range(rows.map(r=>r.completePerMinuteIncludingDrain)),
    maxGeneratorLagMs:Math.max(...rows.map(r=>r.generatorLagMs.max))});
}
// Hash code only; never read environment/config credentials or original POC write paths.
async function sourceDigest(root){
  const hash=createHash('sha256');let count=0;
  async function walk(path,prefix=''){
    const entries=(await readdir(path,{withFileTypes:true})).sort((a,b)=>a.name.localeCompare(b.name));
    for(const e of entries){const rel=prefix+e.name;if(e.isDirectory())await walk(path+'/'+e.name,rel+'/');
      else if(e.isFile()&&e.name.endsWith('.java')){hash.update(rel+'\0');hash.update(await readFile(path+'/'+e.name));count++;}}
  }
  await walk(root);return {files:count,sha256:hash.digest('hex')};
}
const webRoot='/Users/msde76/IdeaProjects/web-back-end';
const provenance={webHead:execFileSync('git',['-C',webRoot,'rev-parse','HEAD'],{encoding:'utf8'}).trim(),
  appHead:execFileSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).trim(),
  webStatus:execFileSync('git',['-C',webRoot,'status','--short'],{encoding:'utf8'}).trim(),
  appJavaSource:await sourceDigest('src/main/java'),webJavaSource:await sourceDigest(webRoot+'/src/main/java'),
  sourceHashTiming:'After run. App working tree contains pre-existing uncommitted changes; hashes identify observed source, not an immutable saved snapshot.',
  sharedDependencies:'Both variants compiled and run against app testRuntimeClasspath; product bootstrap/config/security not loaded.',
  platform:process.platform,arch:process.arch,node:process.version};
const out=file.replace(/\.json$/,'.summary.json');
await writeFile(out,JSON.stringify({source:file,provenance,summary},null,2));
console.log(JSON.stringify({provenance,summary},null,2));
