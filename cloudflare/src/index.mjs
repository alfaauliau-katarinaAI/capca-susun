/**
 * Katarina Vision Lab: user-initiated OCR + privacy-preserving diagnostics.
 * No CAPTCHA worker automation or account access.
 */
const MODEL='@cf/meta/llama-3.2-11b-vision-instruct';
const MAX_IMAGE_BYTES=350000;
const MAX_JSON_BYTES=520000;
const MAX_DIAG_BODY=4096;
const TTL_SECONDS=7*24*60*60;
const HEADERS={'content-type':'application/json; charset=utf-8','cache-control':'no-store','x-content-type-options':'nosniff'};
const out=(data,status=200)=>new Response(JSON.stringify(data),{status,headers:HEADERS});
const validCode=(v)=>typeof v==='string'&&/^[A-Z][A-Z0-9_]{1,55}$/.test(v);

function parseDataUrl(image){
  if(typeof image!=='string')return null;
  const m=/^(data:(image\/(?:png|jpeg));base64,)([a-zA-Z0-9+/]+={0,2})$/.exec(image);
  if(!m||m[3].length%4!==0)return null;
  const payload=m[3],pad=payload.endsWith('==')?2:payload.endsWith('=')?1:0;
  const size=(payload.length/4)*3-pad;
  if(!Number.isSafeInteger(size)||size<=0||size>MAX_IMAGE_BYTES)return null;
  if(m[2]==='image/png'&&!payload.startsWith('iVBORw0KGgo'))return null;
  if(m[2]==='image/jpeg'&&!payload.startsWith('/9j/'))return null;
  return {dataUrl:image,mime:m[2],bytes:size};
}
function authorized(request,secret,header='x-katarina-key'){
  if(typeof secret!=='string'||secret.length<24)return false;
  const got=request.headers.get(header)||'';
  if(got.length!==secret.length)return false;
  let diff=0;for(let i=0;i<got.length;i++)diff|=got.charCodeAt(i)^secret.charCodeAt(i);
  return diff===0;
}
async function getJson(request,maxBytes){
  if(!(request.headers.get('content-type')||'').toLowerCase().startsWith('application/json'))
    return {error:'json_required',status:415};
  if(Number(request.headers.get('content-length'))>maxBytes)return {error:'too_large',status:413};
  let raw;try{raw=await request.text();}catch{return {error:'read_error',status:400};}
  if(raw.length>maxBytes)return {error:'too_large',status:413};
  try{return {data:JSON.parse(raw)};}catch{return {error:'invalid_json',status:400};}
}
function validDiagnostic(body){
  if(!body||typeof body!=='object'||Array.isArray(body))return false;
  if(!/^0\.[0-9]{1,3}\.[0-9]{1,3}$/.test(body.version||''))return false;
  if(!Number.isInteger(body.android_api)||body.android_api<26||body.android_api>60)return false;
  if(!Array.isArray(body.events)||body.events.length<1||body.events.length>20)return false;
  return body.events.every(validCode);
}
async function submitDiagnostic(request,env){
  if(!authorized(request,env.APP_DEVICE_KEY))return out({error:'unauthorized'},401);
  if(!env.DIAGNOSTICS||typeof env.DIAGNOSTICS.put!=='function')return out({error:'diagnostics_not_configured'},503);
  const decoded=await getJson(request,MAX_DIAG_BODY);
  if(decoded.error)return out({error:decoded.error},decoded.status);
  if(!validDiagnostic(decoded.data))return out({error:'invalid_diagnostics'},400);
  const b=decoded.data;
  // Store no IP, cookies, identifiers, secrets, stack traces, screenshots, OCR results or URL.
  const record={created_at:new Date().toISOString(),version:b.version,android_api:b.android_api,events:b.events};
  try{
    const key='diag:'+Date.now().toString().padStart(13,'0')+':'+crypto.randomUUID();
    await env.DIAGNOSTICS.put(key,JSON.stringify(record),{expirationTtl:TTL_SECONDS});
    return out({ok:true,stored:b.events.length});
  }catch{
    return out({error:'diagnostics_store_unavailable'},503);
  }
}
async function summaryDiagnostics(request,env){
  if(!authorized(request,env.DIAG_ADMIN_KEY,'x-katarina-admin-key'))return out({error:'unauthorized'},401);
  if(!env.DIAGNOSTICS||typeof env.DIAGNOSTICS.list!=='function')return out({error:'diagnostics_not_configured'},503);
  try{
    const counts={};
    let reports=0,events=0,cursor=undefined,complete=true;
    // Bound reads, cost and response. 250 reports maximum, aggregate only.
    do{
      const page=await env.DIAGNOSTICS.list({prefix:'diag:',limit:Math.min(100,250-reports),...(cursor?{cursor}:{})});
      for(const entry of page.keys||[]){
        if(reports>=250){complete=false;break;}
        reports++;
        const stored=await env.DIAGNOSTICS.get(entry.name,'json');
        if(!stored||!Array.isArray(stored.events))continue;
        for(const code of stored.events){if(validCode(code)){counts[code]=(counts[code]||0)+1;events++;}}
      }
      cursor=page.list_complete?undefined:page.cursor;
      if(!page.list_complete&&reports>=250)complete=false;
    }while(cursor&&reports<250);
    return out({ok:true,window_days:7,reports,events,counts,complete});
  }catch{return out({error:'diagnostics_read_unavailable'},503);}
}
async function inferImage(request,env){
  if(!authorized(request,env.APP_DEVICE_KEY))return out({error:'unauthorized'},401);
  const decoded=await getJson(request,MAX_JSON_BYTES);
  if(decoded.error)return out({error:decoded.error},decoded.status);
  const image=parseDataUrl(decoded.data?.image);
  if(!image)return out({error:'invalid_image'},400);
  if(!env.AI||typeof env.AI.run!=='function')return out({error:'ai_binding_unavailable'},503);
  try{
    const response=await env.AI.run(MODEL,{
      messages:[
        {role:'system',content:'Transcribe the text in the user-supplied image only. Return an empty response if unreadable.'},
        {role:'user',content:[
          {type:'text',text:'Read any text in this image and return only the transcription.'},
          {type:'image_url',image_url:{url:image.dataUrl}}
        ]}
      ],
      max_tokens:120,temperature:0
    });
    const candidate=typeof response==='string'?response:response?.response??response?.text??'';
    return out({ok:true,text:typeof candidate==='string'?candidate.trim().slice(0,1000):'',engine:'workers-ai',model:MODEL,confidence:null});
  }catch(e){
    // Do not echo vendor error internals or user image.
    console.error('AI infer failed',String(e?.name||'provider_error'));
    return out({error:'ai_unavailable'},502);
  }
}
export default {
  async fetch(request,env){
    const pathname=new URL(request.url).pathname;
    if(pathname==='/health'&&request.method==='GET')
      return out({ok:true,version:'0.6.0',platform:'cloudflare',model:MODEL,diagnostics:!!env.DIAGNOSTICS});
    if(pathname==='/v1/telemetry'&&request.method==='POST')return submitDiagnostic(request,env);
    if(pathname==='/v1/diagnostics-summary'&&request.method==='GET')return summaryDiagnostics(request,env);
    if(pathname==='/v1/ocr'&&request.method==='POST')return inferImage(request,env);
    if(['/v1/telemetry','/v1/diagnostics-summary','/v1/ocr'].includes(pathname))return out({error:'method_not_allowed'},405);
    return out({error:'not_found'},404);
  }
};
export {parseDataUrl,authorized,validDiagnostic};
