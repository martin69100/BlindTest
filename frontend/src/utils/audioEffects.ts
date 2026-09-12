/**
 * Audio effects utility for BlindTest using Web Audio API.
 * Synthesizes game sounds (buzzer, etc.) reliably without external asset dependencies.
 */

let audioCtx: AudioContext | null = null;

function getAudioContext(): AudioContext | null {
  if (typeof window === 'undefined') return null;
  try {
    if (!audioCtx || audioCtx.state === 'closed') {
      const AudioCtxClass =
        window.AudioContext ||
        (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      if (AudioCtxClass) {
        audioCtx = new AudioCtxClass();
      }
    }
    if (audioCtx && audioCtx.state === 'suspended') {
      audioCtx.resume().catch(() => {});
    }
    return audioCtx;
  } catch (e) {
    console.warn('AudioContext indisponible :', e);
    return null;
  }
}

// Déverrouillage automatique au premier geste utilisateur
if (typeof window !== 'undefined') {
  const unlock = () => {
    getAudioContext();
  };
  window.addEventListener('click', unlock, { once: true, passive: true });
  window.addEventListener('keydown', unlock, { once: true, passive: true });
  window.addEventListener('touchstart', unlock, { once: true, passive: true });
}

/**
 * Joue un son de buzzer de jeu télévisé punchy et réaliste (BZZZT !).
 * Respecte le volume et l'état muet configurés dans localStorage.
 */
export const playBuzzerSound = (): void => {
  try {
    const isMuted = localStorage.getItem('blindtest_muted') === 'true';
    if (isMuted) return;

    const savedVol = localStorage.getItem('blindtest_volume');
    const masterVolume =
      savedVol !== null ? Math.max(0, Math.min(1, parseFloat(savedVol))) : 0.8;
    if (masterVolume <= 0) return;

    const ctx = getAudioContext();
    if (!ctx) return;

    const now = ctx.currentTime;
    const duration = 0.38;

    // Enveloppe d'amplitude avec attaque rapide et maintien
    const masterGain = ctx.createGain();
    masterGain.gain.setValueAtTime(0.001, now);
    masterGain.gain.linearRampToValueAtTime(0.4 * masterVolume, now + 0.015);
    masterGain.gain.setValueAtTime(0.4 * masterVolume, now + 0.24);
    masterGain.gain.exponentialRampToValueAtTime(0.0001, now + duration);

    // Filtre passe-bas avec résonance pour un timbre de buzzer chaleureux et percutant
    const filter = ctx.createBiquadFilter();
    filter.type = 'lowpass';
    filter.frequency.setValueAtTime(1400, now);
    filter.Q.setValueAtTime(2.5, now);

    // Oscillateurs en dents de scie désaccordés (150Hz et 154Hz) pour le grésillement caractéristique du buzzer
    const osc1 = ctx.createOscillator();
    osc1.type = 'sawtooth';
    osc1.frequency.setValueAtTime(150, now);

    const osc2 = ctx.createOscillator();
    osc2.type = 'sawtooth';
    osc2.frequency.setValueAtTime(154, now); // Battement rapide 4Hz

    // Oscillateur carré (225Hz) pour apporter du corps et de la présence
    const osc3 = ctx.createOscillator();
    osc3.type = 'square';
    osc3.frequency.setValueAtTime(225, now);

    osc1.connect(filter);
    osc2.connect(filter);
    osc3.connect(filter);
    filter.connect(masterGain);
    masterGain.connect(ctx.destination);

    osc1.start(now);
    osc2.start(now);
    osc3.start(now);

    osc1.stop(now + duration);
    osc2.stop(now + duration);
    osc3.stop(now + duration);
  } catch (err) {
    console.warn('Impossible de jouer le son de buzzer :', err);
  }
};
