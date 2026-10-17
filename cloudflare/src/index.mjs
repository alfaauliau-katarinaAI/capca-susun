/**
 * Cloudflare Worker for user-selected image transcription.
 * Not connected to any CAPTCHA work platform, payment API or worker account.
 */
const MODEL='@cf/meta/llama-3.2-11b-vision-instruct';
const MAX_IMAGE_BYTES=350000;
const MAX_JSON_BYTES=520000;
const HEADERS={'content-type':'application/json; charset=utf-8','cache-control':'no-store','x-content-type-options':'nosniff'};
const out=(data,status=200)=>new Response(JSON.stringify(data),{status,headers:HEADERS});

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
function authorized(request,secret){
  if(typeof secret!=='string'||secret.length<24)return false;
  const got=request.headers.get('x-katarina-key')||'';
  if(got.length!==secret.length)return false;
  let diff=0;for(let i=0;i<got.length;i++)diff|=got.charCodeAt(i)^secret.charCodeAt(i);
  return diff===0;
}
export default {
  async fetch(request,env){
    const url=new URL(request.url);
    if(url.pathname==='/health'&&request.method==='GET')
      return out({ok:true,version:'0.2.0',platform:'cloudflare',model:MODEL});
    if(url.pathname!=='/v1/ocr')return out({error:'not_found'},404);
    if(request.method!=='POST')return out({error:'method_not_allowed'},405);
    if(!authorized(request,env.APP_DEVICE_KEY))return out({error:'unauthorized'},401);
    if(!(request.headers.get('content-type')||'').toLowerCase().startsWith('application/json'))
      return out({error:'json_required'},415);
    if(Number(request.headers.get('content-length'))>MAX_JSON_BYTES)
      return out({error:'too_large'},413);
    let raw;try{raw=await request.text();}catch{return out({error:'read_error'},400);}
    if(raw.length>MAX_JSON_BYTES)return out({error:'too_large'},413);
    let body;try{body=JSON.parse(raw);}catch{return out({error:'invalid_json'},400);}
    const image=parseDataUrl(body.image);
    if(!image)return out({error:'invalid_image'},400);
    if(!env.AI||typeof env.AI.run!=='function')return out({error:'ai_binding_unavailable'},503);
    try{
      const response=await env.AI.run(MODEL,{
        messages:[
          {role:'system',content:'Transcribe text only from the user-selected image. If unreadable return empty string; do not guess or add commentary.'},
          {role:'user',content:'Transcribe readable text exactly. Return only transcription.'}
        ],
        image:image.dataUrl,
        max_tokens:120,temperature:0,
      });
      const candidate=typeof response==='string'?response:response?.response??response?.text??'';
      return out({ok:true,text:typeof candidate==='string'?candidate.trim().slice(0,1000):'',engine:'workers-ai',model:MODEL,confidence:null});
    }catch(e){
      console.error('Workers AI request failed',String(e?.name||'provider_error'));
      return out({error:'ai_unavailable'},502);
    }
  }
};
export {parseDataUrl,authorized};
