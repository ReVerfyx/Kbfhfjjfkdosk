(() => {
  const alarm = document.getElementById('globalAlarm');
  if (!alarm) return;

  let started = false;
  let ctx, osc, gain, timer;

  function startAlarm() {
    if (started) return;
    started = true;
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
        high = !high;
        osc.frequency.setValueAtTime(high ? 920 : 610, ctx.currentTime);
      };
      tick();
      timer = setInterval(tick, 330);
      setTimeout(stopAlarm, 12000);
    } catch (_) {}
  }

  function stopAlarm() {
    try { clearInterval(timer); } catch (_) {}
    try { osc && osc.stop(); } catch (_) {}
    try { ctx && ctx.close(); } catch (_) {}
  }

  // Browsers may block autoplay audio. We try immediately and retry on the first gesture.
  startAlarm();
  ['pointerdown','touchstart','keydown'].forEach(ev =>
    document.addEventListener(ev, startAlarm, { once: true, passive: true })
  );
})();