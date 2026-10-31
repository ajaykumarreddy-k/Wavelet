/* ==========================================================================
   Wavelet — Interactive Logic & Ambient Soundscape
   ========================================================================== */

document.addEventListener('DOMContentLoaded', () => {
  initNavbar();
  initParallax();
  initCharms();
});

/* --------------------------------------------------------------------------
   1. Dynamic Navbar on Scroll
   -------------------------------------------------------------------------- */
function initNavbar() {
  const header = document.getElementById('site-header');
  if (!header) return;

  const onScroll = () => {
    if (window.scrollY > 30) {
      header.classList.add('scrolled');
    } else {
      header.classList.remove('scrolled');
    }
  };

  window.addEventListener('scroll', onScroll, { passive: true });
  onScroll();
}

/* --------------------------------------------------------------------------
   2. Interactive Mouse Parallax for Hero 3D Charms
   -------------------------------------------------------------------------- */
function initParallax() {
  const hero = document.getElementById('hero');
  const charms = document.querySelectorAll('.charm');
  if (!hero || !charms.length) return;

  // Check for reduced motion preference
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    return;
  }

  let mouseX = 0;
  let mouseY = 0;
  let currentX = 0;
  let currentY = 0;

  hero.addEventListener('mousemove', (e) => {
    const rect = hero.getBoundingClientRect();
    const x = (e.clientX - rect.left) / rect.width - 0.5;
    const y = (e.clientY - rect.top) / rect.height - 0.5;

    mouseX = x * 35; // Maximum pixel displacement
    mouseY = y * 35;
  });

  hero.addEventListener('mouseleave', () => {
    mouseX = 0;
    mouseY = 0;
  });

  // Smooth lerp loop
  function render() {
    currentX += (mouseX - currentX) * 0.08;
    currentY += (mouseY - currentY) * 0.08;

    charms.forEach((charm, index) => {
      const depth = (index % 3 + 1) * 0.45;
      const offsetX = currentX * depth;
      const offsetY = currentY * depth;
      charm.style.setProperty('--parallax-x', `${offsetX}px`);
      charm.style.setProperty('--parallax-y', `${offsetY}px`);
      charm.style.transform = `translate(${offsetX}px, ${offsetY}px)`;
    });

    requestAnimationFrame(render);
  }

  requestAnimationFrame(render);
}

/* --------------------------------------------------------------------------
   3. 3D Charms Interaction & Floating Story Drawer
   -------------------------------------------------------------------------- */
function initCharms() {
  const charms = document.querySelectorAll('.charm');
  const modal = document.getElementById('charmModal');
  const modalImg = document.getElementById('charmModalImg');
  const modalTitle = document.getElementById('charmModalTitle');
  const modalDesc = document.getElementById('charmModalDesc');
  const modalClose = document.getElementById('charmModalClose');
  const featureCards = document.querySelectorAll('.charm-feature-card');

  const charmData = {
    gem: {
      title: "Wavelet Red Gem",
      desc: "The iconic Wavelet brand mark, sculpted in liquid glass & ruby reflections. A symbol of custom-fit music craftsmanship.",
      img: "assets/3d/charm_wavelet_gem.png"
    },
    sphere: {
      title: "Lossless Audio Sphere",
      desc: "Suspended in clear water-glass with rainbow refraction. Hi-Res FLAC and ALAC audio decoding with YouTube fallback.",
      img: "assets/3d/charm_audio_sphere.png"
    },
    pill: {
      title: "Harmonic Capsule",
      desc: "Dual-tone jelly pill embodying 0–12s gapless crossfade curves, silence trimming, and beat-matched Automix transitions.",
      img: "assets/3d/charm_jelly_pill.png"
    },
    torus: {
      title: "Spectrum Vinyl Disc",
      desc: "Holographic grooved disc uniting infinite YouTube Music discovery with your offline local files under one seamless library.",
      img: "assets/3d/charm_vinyl_torus.png"
    },
    ribbon: {
      title: "Fluid Soundwave",
      desc: "Weightless liquid glass wave that mirrors Apple-style fluid lyrics with multi-provider fallbacks (LRCLIB, Genius, KuGou).",
      img: "assets/3d/charm_liquid_wave.png"
    },
    play: {
      title: "Instant Lazy Import",
      desc: "Tactile ruby play charm. Paste any YouTube playlist link; your listening starts instantly while caching runs in the background.",
      img: "assets/3d/charm_red_play.png"
    }
  };

  function openCharmModal(key) {
    const data = charmData[key];
    if (!data || !modal) return;

    modalImg.src = data.img;
    modalImg.alt = data.title;
    modalTitle.textContent = data.title;
    modalDesc.textContent = data.desc;

    modal.classList.add('active');
    modal.setAttribute('aria-hidden', 'false');

    playChime(660); // Friendly harmonic sound
  }

  function closeCharmModal() {
    if (!modal) return;
    modal.classList.remove('active');
    modal.setAttribute('aria-hidden', 'true');
  }

  charms.forEach((charm) => {
    charm.addEventListener('click', (e) => {
      e.stopPropagation();
      const key = charm.getAttribute('data-charm');
      openCharmModal(key);
    });

    charm.addEventListener('mouseenter', () => {
      playSoftHover();
    });
  });

  if (modalClose) {
    modalClose.addEventListener('click', (e) => {
      e.stopPropagation();
      closeCharmModal();
    });
  }

  document.addEventListener('click', (e) => {
    if (modal && modal.classList.contains('active') && !modal.contains(e.target)) {
      closeCharmModal();
    }
  });

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      closeCharmModal();
    }
  });

  // Feature cards linking to modal
  featureCards.forEach((card) => {
    card.addEventListener('click', () => {
      const target = card.getAttribute('data-charm-target');
      openCharmModal(target);
    });
  });
}

/* --------------------------------------------------------------------------
   4. Ethereal Ambient Soundscape (Web Audio API Synthesizer)
   -------------------------------------------------------------------------- */
let audioCtx = null;
let isPlaying = false;
let isMuted = false;
let ambientInterval = null;

function getAudioContext() {
  if (!audioCtx) {
    const AudioContext = window.AudioContext || window.webkitAudioContext;
    audioCtx = new AudioContext();
  }
  if (audioCtx.state === 'suspended') {
    audioCtx.resume();
  }
  return audioCtx;
}

function playChime(freq = 523.25) {
  try {
    const ctx = getAudioContext();
    if (isMuted) return;

    const osc = ctx.createOscillator();
    const gain = ctx.createGain();

    osc.type = 'sine';
    osc.frequency.setValueAtTime(freq, ctx.currentTime);

    // Warm envelope
    gain.gain.setValueAtTime(0, ctx.currentTime);
    gain.gain.linearRampToValueAtTime(0.12, ctx.currentTime + 0.04);
    gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 1.2);

    osc.connect(gain);
    gain.connect(ctx.destination);

    osc.start();
    osc.stop(ctx.currentTime + 1.2);
  } catch (err) {
    console.debug('Audio interaction notice:', err);
  }
}

function playSoftHover() {
  try {
    const ctx = getAudioContext();
    if (isMuted) return;

    const osc = ctx.createOscillator();
    const gain = ctx.createGain();

    osc.type = 'sine';
    osc.frequency.setValueAtTime(880, ctx.currentTime);

    gain.gain.setValueAtTime(0, ctx.currentTime);
    gain.gain.linearRampToValueAtTime(0.02, ctx.currentTime + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 0.25);

    osc.connect(gain);
    gain.connect(ctx.destination);

    osc.start();
    osc.stop(ctx.currentTime + 0.25);
  } catch (err) {
    // Ignore audio unprimed errors
  }
}


