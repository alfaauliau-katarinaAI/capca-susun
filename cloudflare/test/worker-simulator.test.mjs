import test from 'node:test';
import assert from 'node:assert/strict';
import worker from '../src/index.mjs';
import {createSimulatedWorker} from '../src/worker-simulator.mjs';

const KEY='a-very-long-random-test-key-12345678';
const PNG='iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/By8AAAAASUVORK5CYII=';
const IMG='data:image/png;base64,'+PNG;
const base=Date.parse('2026-10-17T00:00:00Z');
const task=(id='t1',changes={})=>({id,kind:'image_text',image:IMG,deadlineMs:base+60000,simulation:true,...changes});

function fixture({output='TEST42',cloudFailure=false,clock=()=>base,expected='TEST42',acceptedCredits=0.5}={}){
  const calls={ai:0,send:0,skip:0,received:[]};
  const recognize=async(image)=>{
    calls.ai++;
    const env={APP_DEVICE_KEY:KEY,AI:{run:async()=>{if(cloudFailure)throw Error('fake AI unavailable');return {response:output};}}};
    const req=new Request('https://local.test/v1/ocr',{
      method:'POST',headers:{'X-Katarina-Key':KEY,'Content-Type':'application/json'},
      body:JSON.stringify({image})
    });
    const res=await worker.fetch(req,env);
    if(!res.ok)throw Error('OCR_HTTP_'+res.status);
    return (await res.json()).text;
  };
  const submit=async({id,answer})=>{
    calls.send++;calls.received.push({id,answer});
    if(answer!==expected)return {accepted:false,simulatedCredits:0};
    return {accepted:true,simulatedCredits:acceptedCredits};
  };
  const skip=async({id,reason})=>{calls.skip++;calls.received.push({id,reason})};
  return {runner:createSimulatedWorker({recognize,submit,skip,now:clock}),calls};
}
test('synthetic image task -> Cloudflare mock AI -> accepted mock receipt -> simulated credit',async()=>{
  const {runner,calls}=fixture();
  const result=await runner.run(task());
  assert.deepEqual(result,{id:'t1',status:'accepted_simulation',credited:0.5});
  assert.equal(calls.ai,1);assert.equal(calls.send,1);assert.equal(calls.skip,0);
  assert.deepEqual(calls.received,[{id:'t1',answer:'TEST42'}]);
});
test('incorrect OCR is rejected by simulated task server and never credited',async()=>{
  const {runner,calls}=fixture({output:'WRONG'});
  const result=await runner.run(task());
  assert.equal(result.status,'rejected');assert.equal(result.credited,0);
  assert.equal(calls.send,1);
});
test('duplicate task id never triggers a second OCR or payment',async()=>{
  const {runner,calls}=fixture();
  await runner.run(task('same'));
  const duplicate=await runner.run(task('same'));
  assert.equal(duplicate.status,'duplicate');assert.equal(duplicate.credited,0);
  assert.equal(calls.ai,1);assert.equal(calls.send,1);
});
test('task already inside safety margin skips immediately, never spending AI calls',async()=>{
  const {runner,calls}=fixture();
  const result=await runner.run(task('late',{deadlineMs:base+4000}));
  assert.equal(result.reason,'expired');assert.equal(calls.ai,0);assert.equal(calls.skip,1);
});
test('OCR finishing too near deadline skips rather than submitting a stale answer',async()=>{
  let t=base;
  const {runner,calls}=fixture({clock:()=>t});
  const other=createSimulatedWorker({
    now:()=>t,
    recognize:async()=>{t+=56000;return 'TEST42'},
    submit:async()=>{calls.send++;return {accepted:true,simulatedCredits:1}},
    skip:async()=>{calls.skip++}
  });
  const result=await other.run(task('timeout'));
  assert.equal(result.reason,'deadline_reached');assert.equal(calls.send,0);assert.equal(calls.skip,1);
  void runner;
});
test('empty, multiline or punctuation-containing OCR is never submitted',async()=>{
  for(const bad of ['', 'AB CD', 'Hello\\nWorld','<script>']){
    const {runner,calls}=fixture({output:bad});
    const result=await runner.run(task());
    assert.equal(result.reason,'unreliable_answer');assert.equal(calls.send,0);assert.equal(calls.skip,1);
  }
});
test('Cloudflare inference failure is skipped with zero simulated credit',async()=>{
  const {runner,calls}=fixture({cloudFailure:true});
  const result=await runner.run(task());
  assert.equal(result.reason,'ocr_unavailable');assert.equal(result.credited,0);
  assert.equal(calls.send,0);assert.equal(calls.skip,1);
});
test('malformed task and unsupported type are rejected without sending',async()=>{
  const {runner,calls}=fixture();
  const result=await runner.run(task('bad',{kind:'recaptcha_v2'}));
  assert.equal(result.reason,'unsupported_image_type');assert.equal(calls.send,0);
  await assert.rejects(runner.run(task('bad id',{id:'../../bad id'})),/invalid/);
});
test('failed simulated submission is skipped and cannot duplicate',async()=>{
  let sent=0,skipped=0;
  const runner=createSimulatedWorker({
    now:()=>base,
    recognize:async()=> 'TEST42',
    submit:async()=>{sent++;throw Error('fake remote connection lost');},
    skip:async()=>{skipped++;}
  });
  const result=await runner.run(task());
  assert.equal(result.reason,'submit_unavailable');
  assert.equal((await runner.run(task())).status,'duplicate');
  assert.equal(sent,1);assert.equal(skipped,1);
});
test('live 2Captcha tasks are strictly disallowed, even if callback exists',async()=>{
  const {runner,calls}=fixture();
  await assert.rejects(runner.run(task('live',{simulation:false})),/SIMULATION_ONLY/);
  assert.equal(calls.ai,0);assert.equal(calls.send,0);
});
