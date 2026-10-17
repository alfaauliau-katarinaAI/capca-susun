import test from 'node:test';
import assert from 'node:assert/strict';
import worker,{parseDataUrl,authorized} from '../src/index.mjs';
const KEY='a-very-long-random-test-key-12345678';
const PNG='iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/By8AAAAASUVORK5CYII=';
const IMAGE='data:image/png;base64,'+PNG;
const url='https://example.test/v1/ocr';
const request=(body={image:IMAGE},key=KEY)=>new Request(url,{method:'POST',headers:{'Content-Type':'application/json','X-Katarina-Key':key},body:JSON.stringify(body)});
const env={APP_DEVICE_KEY:KEY,AI:{run:async()=>({response:'TEST42'})}};
test('health endpoint',async()=>assert.equal((await worker.fetch(new Request('https://example.test/health'),env)).status,200));
test('successful OCR mock',async()=>{
  let calls=0;const e={...env,AI:{run:async(model,opts)=>{calls++;assert.ok(model.includes('vision'));assert.equal(opts.image,IMAGE);return {response:'ABC42'};}}};
  const r=await worker.fetch(request(),e);assert.equal(r.status,200);assert.equal((await r.json()).text,'ABC42');assert.equal(calls,1);
});
test('wrong key rejected',async()=>{assert.equal((await worker.fetch(request({image:IMAGE},'wrong'),env)).status,401);assert.equal(authorized(new Request(url),KEY),false);});
test('oversized rejected',async()=>{
  const img='data:image/png;base64,'+Buffer.alloc(351000,5).toString('base64');
  assert.equal((await worker.fetch(request({image:img}),env)).status,400);
});
test('other types rejected',()=>assert.equal(parseDataUrl('data:text/html;base64,YQ=='),null));
test('invalid JSON rejected',async()=>{
  const r=new Request(url,{method:'POST',headers:{'Content-Type':'application/json','X-Katarina-Key':KEY},body:'{bad'});
  assert.equal((await worker.fetch(r,env)).status,400);
});
test('missing AI binding rejected',async()=>assert.equal((await worker.fetch(request(),{APP_DEVICE_KEY:KEY})).status,503));
