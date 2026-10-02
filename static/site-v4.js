(()=>{
  document.querySelectorAll('[data-autogrow]').forEach(t=>{
    const f=()=>{t.style.height='auto';t.style.height=Math.min(t.scrollHeight,220)+'px'};
    t.addEventListener('input',f);f()
  });

  const parseUtc=(raw)=>{
    if(!raw)return null;
    let v=String(raw).trim();
    if(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(v)) v=v.replace(' ','T')+'Z';
    else if(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?$/.test(v)) v+='Z';
    const d=new Date(v);
    return Number.isNaN(d.getTime())?null:d;
  };

  const formatTimes=async()=>{
    let zone=null;
    try{
      const r=await fetch('/api/v1/timezone',{cache:'no-store',headers:{'Accept':'application/json'}});
      if(r.ok){
        const data=await r.json();
        if(data && data.timezone && data.timezone!=='UTC') zone=data.timezone;
      }
    }catch(e){}
    if(!zone){
      try{zone=Intl.DateTimeFormat().resolvedOptions().timeZone}catch(e){}
    }
    document.querySelectorAll('.local-time[data-utc]').forEach(el=>{
      const d=parseUtc(el.dataset.utc);
      if(!d)return;
      try{
        el.textContent=new Intl.DateTimeFormat('ru-RU',{
          timeZone:zone||'UTC',
          day:'2-digit',month:'2-digit',year:'numeric',
          hour:'2-digit',minute:'2-digit',
          hour12:false
        }).format(d).replace(',','');
        if(zone)el.title=zone;
      }catch(e){
        el.textContent=d.toLocaleString('ru-RU');
      }
    });
  };
  formatTimes();

  const alarm=document.getElementById('globalAlarm');
  if(alarm){
    let ctx,osc,gain,timer,started=false;
    const start=()=>{
      if(started)return;started=true;
      try{
        const AC=window.AudioContext||window.webkitAudioContext;if(!AC)return;
        ctx=new AC();osc=ctx.createOscillator();gain=ctx.createGain();
        osc.type='sawtooth';gain.gain.value=.025;osc.connect(gain).connect(ctx.destination);osc.start();
        let hi=false;
        timer=setInterval(()=>{hi=!hi;osc.frequency.setValueAtTime(hi?880:590,ctx.currentTime)},340);
        setTimeout(()=>{clearInterval(timer);try{osc.stop();ctx.close()}catch(e){}},9000)
      }catch(e){}
    };
    ['pointerdown','keydown','touchstart'].forEach(e=>document.addEventListener(e,start,{once:true,passive:true}))
  }
})();
window.sharePost=async u=>{
  try{
    if(navigator.share)await navigator.share({url:u});
    else{await navigator.clipboard.writeText(u);alert('Ссылка скопирована')}
  }catch(e){}
};
