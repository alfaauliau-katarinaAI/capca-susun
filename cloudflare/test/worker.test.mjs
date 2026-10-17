import test from 'node:test';
import assert from 'node:assert/strict';
import worker,{parseDataUrl,authorized,validDiagnostic} from '../src/index.mjs';
const KEY='a-very-long-random-test-key-12345678';
const ADMIN='a-different-long-admin-test-key-123';
const PNG='iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/By8AAAAASUVORK5CYII=';
const IMAGE='data:image/png;base64,'+PNG;
const req=(path,body,key=KEY)=>new Request('https://example.test'+path,{method:'POST',headers:{'Content-Type':'application/json','X-Katarina-Key':key},body:JSON.stringify(body)});
const stored=new Map();
const KV={
  put:async(key,val,options)=>{if(key.startsWith('diag:'))assert.equal(options.expirationTtl,7*24*60*60);stored.set(key,JSON.parse(val));},
  get:async(key)=>stored.get(key)||null,
  list:async()=>({keys:[...stored.keys()].map(name=>({name})),list_complete:true})
};
const env={APP_DEVICE_KEY:KEY,DIAG_ADMIN_KEY:ADMIN,DIAGNOSTICS:KV,AI:{run:async()=>({response:'TEST42'})}};
test('health endpoint',async()=>{const r=await worker.fetch(new Request('https://example.test/health'),env);assert.equal(r.status,200);assert.equal((await r.json()).diagnostics,true)});
test('OCR uses image within model messages',async()=>{
  let calls=0;const e={...env,AI:{run:async(model,opts)=>{calls++;assert.ok(model.includes('vision'));assert.equal(opts.messages[1].content[1].image_url.url,IMAGE);return {response:'ABC42'};}}};
  const r=await worker.fetch(req('/v1/ocr',{image:IMAGE}),e);assert.equal(r.status,200);assert.equal((await r.json()).text,'ABC42');assert.equal(calls,1);
});
test('unauthorized OCR and telemetry are rejected',async()=>{
  assert.equal((await worker.fetch(req('/v1/ocr',{image:IMAGE},'wrong'),env)).status,401);
  assert.equal((await worker.fetch(req('/v1/telemetry',{version:'0.6.0',android_api:30,events:['CLOUD_HTTP_502']},'wrong'),env)).status,401);
  assert.equal(authorized(new Request('https://x.test'),KEY),false);
});
test('invalid image rejected',async()=>{
  assert.equal((await worker.fetch(req('/v1/ocr',{image:'data:text/html;base64,YQ=='}),env)).status,400);
  assert.equal(parseDataUrl('data:text/html;base64,YQ=='),null);
});
test('oversized image rejected',async()=>{
  const image='data:image/png;base64,'+Buffer.alloc(351000,5).toString('base64');
  assert.equal((await worker.fetch(req('/v1/ocr',{image}),env)).status,400);
});
test('bad JSON rejected',async()=>{
  const r=new Request('https://example.test/v1/ocr',{method:'POST',headers:{'Content-Type':'application/json','X-Katarina-Key':KEY},body:'{bad'});
  assert.equal((await worker.fetch(r,env)).status,400);
});
test('missing AI binding rejected',async()=>assert.equal((await worker.fetch(req('/v1/ocr',{image:IMAGE}),{APP_DEVICE_KEY:KEY})).status,503));
test('diagnostics stores anonymized error codes with 7 day TTL',async()=>{
  stored.clear();
  const response=await worker.fetch(req('/v1/telemetry',{version:'0.6.0',android_api:30,events:['OCR_LOCAL_FAILED','CLOUD_HTTP_502']}),env);
  assert.equal(response.status,200);
  assert.equal(stored.size,1);
  const record=[...stored.values()][0];assert.deepEqual(record.events,['OCR_LOCAL_FAILED','CLOUD_HTTP_502']);
  assert.deepEqual(Object.keys(record).sort(),['android_api','created_at','events','version']);
});
test('diagnostics rejects secrets, free text, extra-long event codes',async()=>{
  for(const code of ['password=1234','token-test','IMAGE_DATA:abc','A'.repeat(60)]){
    assert.equal(validDiagnostic({version:'0.6.0',android_api:30,events:[code]}),false);
  }
  assert.equal((await worker.fetch(req('/v1/telemetry',{version:'0.6.0',android_api:30,events:['client_key=123']}),env)).status,400);
});
test('diagnostics requires storage configured',async()=>{
  assert.equal((await worker.fetch(req('/v1/telemetry',{version:'0.6.0',android_api:30,events:['CLOUD_HTTP_502']}),{APP_DEVICE_KEY:KEY})).status,503);
});
test('admin diagnostic summary is authenticated, aggregated and anonymous',async()=>{
  const unauthorized=await worker.fetch(new Request('https://example.test/v1/diagnostics-summary'),env);
  assert.equal(unauthorized.status,401);
  const summary=await worker.fetch(new Request('https://example.test/v1/diagnostics-summary',{headers:{'X-Katarina-Admin-Key':ADMIN}}),env);
  assert.equal(summary.status,200);const data=await summary.json();
  assert.equal(data.events,2);
  assert.equal(data.counts.CLOUD_HTTP_502,1);
  assert.ok(!JSON.stringify(data).includes('created_at'));
});
test('telemetry does not accept GET',async()=>assert.equal((await worker.fetch(new Request('https://example.test/v1/telemetry'),env)).status,405));


test('pairing request, approval, claim and device OCR token work end to end',async()=>{
  // Actual RSA-OAEP encryption in workers runtime and decryption with matching private key
  stored.clear();
  const kp=await crypto.subtle.generateKey({name:'RSA-OAEP',modulusLength:2048,publicExponent:new Uint8Array([1,0,1]),hash:'SHA-1'},true,['encrypt','decrypt']);
  const exported=Buffer.from(await crypto.subtle.exportKey('spki',kp.publicKey)).toString('base64');
  const pair=await worker.fetch(req('/v1/pair/start',{public_key:exported}),env);
  assert.equal(pair.status,200);
  const begun=await pair.json();
  assert.match(begun.code,/^[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}$/);
  assert.equal((await worker.fetch(req('/v1/pair/claim',{code:begun.code,claim:begun.claim}),env)).status,202);
  const noAdmin=await worker.fetch(req('/v1/pair/approve',{code:begun.code}),env);
  assert.equal(noAdmin.status,401);
  const adminApproval=await worker.fetch(new Request('https://example.test/v1/pair/approve',{method:'POST',headers:{'Content-Type':'application/json','X-Katarina-Admin-Key':ADMIN},body:JSON.stringify({code:begun.code})}),env);
  assert.equal(adminApproval.status,200);
  const claimed=await worker.fetch(req('/v1/pair/claim',{code:begun.code,claim:begun.claim}),env);
  assert.equal(claimed.status,200);
  const cryptic=(await claimed.json()).encrypted_token;
  const token=new TextDecoder().decode(await crypto.subtle.decrypt({name:'RSA-OAEP'},kp.privateKey,Buffer.from(cryptic,'base64')));
  assert.ok(token.length>=30);
  const recognized=await worker.fetch(req('/v1/ocr',{image:IMAGE},token),env);
  assert.equal(recognized.status,200);
  assert.equal((await recognized.json()).text,'TEST42');
});
