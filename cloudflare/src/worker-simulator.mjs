/**
 * Katarina worker task lifecycle rehearsal.
 * STRICTLY SIMULATED: no 2Captcha endpoints, credentials or paid work.
 * Synthetic, self-owned tests only. Kept separate from deployed Worker routes.
 */
export function createSimulatedWorker({recognize,submit,skip=async()=>{},now=Date.now,safetyMs=5000}={}){
  if(typeof recognize!=='function'||typeof submit!=='function'||typeof skip!=='function')
    throw new TypeError('simulated callbacks must be functions');
  const processed=new Set();
  const done=new Map();
  async function fail(task,reason){
    try{await skip({id:task.id,reason});}
    catch{return {id:task.id,status:'error',reason:'skip_failed',credited:0};}
    return {id:task.id,status:'skipped',reason,credited:0};
  }
  async function run(task){
    if(!task||task.simulation!==true)throw new Error('SIMULATION_ONLY: cannot operate on live Worker tasks');
    if(typeof task.id!=='string'||!/^[a-zA-Z0-9_-]{1,64}$/.test(task.id))
      throw new TypeError('synthetic task ID invalid');
    if(processed.has(task.id))return {id:task.id,status:'duplicate',credited:0};
    processed.add(task.id);
    if(task.kind!=='image_text'||typeof task.image!=='string'||!task.image.startsWith('data:image/'))
      return done.set(task.id,await fail(task,'unsupported_image_type')).get(task.id);
    if(!Number.isSafeInteger(task.deadlineMs)||now()>=task.deadlineMs-safetyMs)
      return done.set(task.id,await fail(task,'expired')).get(task.id);
    let answer;
    try{answer=await recognize(task.image);}
    catch{return done.set(task.id,await fail(task,'ocr_unavailable')).get(task.id);}
    if(now()>=task.deadlineMs-safetyMs)
      return done.set(task.id,await fail(task,'deadline_reached')).get(task.id);
    if(typeof answer!=='string'||!/^[A-Za-z0-9]{1,20}$/.test(answer.trim()))
      return done.set(task.id,await fail(task,'unreliable_answer')).get(task.id);
    try{
      const receipt=await submit({id:task.id,answer:answer.trim()});
      if(receipt?.accepted!==true)return done.set(task.id,{id:task.id,status:'rejected',reason:'answer_rejected',credited:0}).get(task.id);
      const credited=Number.isFinite(receipt.simulatedCredits)&&receipt.simulatedCredits>=0
        ?receipt.simulatedCredits:0;
      return done.set(task.id,{id:task.id,status:'accepted_simulation',credited}).get(task.id);
    }catch{
      return done.set(task.id,await fail(task,'submit_unavailable')).get(task.id);
    }
  }
  return {run,history:()=>Array.from(done.values()),count:()=>processed.size};
}
