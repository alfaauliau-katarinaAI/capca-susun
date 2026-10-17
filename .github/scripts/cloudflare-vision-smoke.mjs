import assert from 'node:assert/strict';
import {deflateSync} from 'node:zlib';

// A user-owned synthetic black-on-white image: "TEST".
const glyphs={
  T:['11111','00100','00100','00100','00100','00100','00100'],
  E:['11111','10000','10000','11110','10000','10000','11111'],
  S:['01111','10000','10000','01110','00001','00001','11110']
};
const word='TEST',scale=6,w=210,h=74,px=Buffer.alloc(w*h*3,255);
let x0=12;
for(const letter of word){
  const grid=glyphs[letter];
  for(let y=0;y<7;y++)for(let x=0;x<5;x++)if(grid[y][x]==='1'){
    for(let sy=0;sy<scale;sy++)for(let sx=0;sx<scale;sx++){
      const at=((10+y*scale+sy)*w+x0+x*scale+sx)*3;
      px[at]=px[at+1]=px[at+2]=0;
    }
  }
  x0+=6*scale;
}
function crc32(buf){let n=~0;for(const byte of buf){n^=byte;for(let i=0;i<8;i++)n=n&1?(n>>>1)^0xEDB88320:n>>>1;}return (~n)>>>0;}
function chunk(type,buf){const t=Buffer.from(type),len=Buffer.alloc(4),crc=Buffer.alloc(4);len.writeUInt32BE(buf.length);crc.writeUInt32BE(crc32(Buffer.concat([t,buf])));return Buffer.concat([len,t,buf,crc]);}
const header=Buffer.alloc(13);
header.writeUInt32BE(w,0);header.writeUInt32BE(h,4);header[8]=8;header[9]=2;
const scan=Buffer.alloc((w*3+1)*h);
for(let y=0;y<h;y++)px.copy(scan,(w*3+1)*y+1,w*3*y,w*3*(y+1));
const png=Buffer.concat([Buffer.from('89504e470d0a1a0a','hex'),chunk('IHDR',header),chunk('IDAT',deflateSync(scan)),chunk('IEND',Buffer.alloc(0))]);
const base=process.env.KATARINA_BASE_URL||'https://katarina-vision-lab.alfaaulia8.workers.dev';
const key=process.env.KATARINA_DEVICE_KEY||'';
if(key.length<24)throw new Error('Missing device key in GitHub Actions secrets');
const signal=AbortSignal.timeout(45000);
const health=await fetch(base+'/health',{signal});
assert.equal(health.status,200,'Health endpoint must return 200');
const response=await fetch(base+'/v1/ocr',{
  method:'POST',signal,
  headers:{'Content-Type':'application/json','X-Katarina-Key':key},
  body:JSON.stringify({image:'data:image/png;base64,'+png.toString('base64')})
});
const result=await response.json();
console.log('HEALTH_HTTP',health.status);
console.log('OCR_HTTP',response.status);
console.log('OCR_ENGINE',result.engine||'unknown');
console.log('OCR_TEXT_LENGTH',String(result.text||'').length);
if(response.status!==200||result.ok!==true)throw new Error('Workers AI inference not ready: '+String(result.error||'http_'+response.status));
console.log('INFERENCE_SUCCESS: Cloudflare Workers AI accepted synthetic image.');
