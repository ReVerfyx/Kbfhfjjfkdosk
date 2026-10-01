(() => {
  const alarm = document.getElementById('globalAlarm');
  if (!alarm) return;

  let ctx = null, osc = null, gain = null, timer = null, stopped = false;

  function buildAlarm() {
    if (ctx || stopped) return;
    try {
      const AC = window.AudioContext || window.webkitAudioContext;
      if (!AC) return;
      ctx = new AC();
      osc = ctx.createOscillator();
      gain = ctx.createGain();
      osc.type = 'sawtooth';
      gain.gain.value = 0.028;
      osc.connect(gain).connect(ctx.destination);
      osc.start();
      let high = false;
      const tick = () => {
        if (!ctx || stopped) return;
        high = !high;
        osc.frequency.setValueAtTime(high ? 920 : 610, ctx.currentTime);
      };
      tick();
      timer = setInterval(tick, 330);
      setTimeout(stopAlarm, 12000);
    } catch (_) {}
  }

  function tryStart() {
    buildAlarm();
    if (ctx && ctx.state === 'suspended') {
      ctx.resume().catch(() => {});
    }
  }

  function stopAlarm() {
    if (stopped) return;
    stopped = true;
    try { clearInterval(timer); } catch (_) {}
    try { osc && osc.stop(); } catch (_) {}
    try { ctx && ctx.close(); } catch (_) {}
  }

  // Try immediately. If autoplay audio is blocked, the first tap/key resumes it.
  tryStart();
  ['pointerdown','touchstart','keydown'].forEach(ev =>
    document.addEventListener(ev, tryStart, { passive: true })
  );
})();