"use strict";
// Pantalla de carga: al presionar Enter, el martillo entra en arco desde
// la esquina inferior derecha dejando una estela, golpea el suelo con un
// impact frame, suelta chispas y de ellas sale "RentTool".
(() => {
  const splash = document.getElementById("splash");
  if (!splash) return;
  if (document.documentElement.classList.contains("skip-splash")) {
    splash.remove();
    return;
  }
  const root = document.documentElement,
    stage = splash.querySelector(".splash-stage"),
    hammer = splash.querySelector(".splash-hammer"),
    swing = splash.querySelector(".splash-swing"),
    impact = splash.querySelector(".splash-impact"),
    floor = splash.querySelector(".splash-floor"),
    word = splash.querySelector(".splash-word"),
    trail = splash.querySelector(".splash-trail"),
    reduced = matchMedia("(prefers-reduced-motion: reduce)").matches,
    wait = (ms) => new Promise((r) => setTimeout(r, ms));

  const text = word.textContent.trim();
  const letters = [...text].map((ch, i) => {
    const span = document.createElement("span");
    span.className = "splash-letter" + (i === text.length - 1 ? " is-dot" : "");
    span.textContent = ch;
    return span;
  });
  word.replaceChildren(...letters);
  root.classList.add("splash-lock");
  // Con el script activo la pantalla espera a la persona: se quita el respaldo.
  splash.style.animation = "none";

  // Sonidos: whoosh del martillazo, golpe de metal y whoosh de cierre.
  // Los navegadores bloquean el audio hasta que la persona interactúa con la
  // página; por eso la animación arranca al presionar Enter.
  const sounds = { "whoosh-in": null, smash: null, "whoosh-out": null };
  let audio = null;
  try {
    audio = new AudioContext();
  } catch {}
  const loadSounds = Promise.all(
    Object.keys(sounds).map(async (name) => {
      try {
        const res = await fetch(`assets/sounds/${name}.mp3`);
        sounds[name] = await audio.decodeAudioData(await res.arrayBuffer());
      } catch {}
    }),
  );
  // Se entra con la tecla Enter; en pantallas táctiles, que no tienen
  // teclado, con un toque.
  const gate = splash.querySelector(".splash-enter");
  if (matchMedia("(pointer: coarse)").matches)
    gate.querySelector(".splash-hint").textContent =
      "Toca la pantalla para continuar";
  const entered = new Promise((resolve) => {
    const enter = () => {
      removeEventListener("keydown", onKey);
      splash.removeEventListener("pointerup", onTouch);
      audio?.resume().catch(() => {});
      resolve();
    };
    const onKey = (e) => e.key === "Enter" && enter();
    const onTouch = (e) => e.pointerType !== "mouse" && enter();
    addEventListener("keydown", onKey);
    splash.addEventListener("pointerup", onTouch);
  });
  function playSound(name, { delay = 0, offset = 0, gain = 1 } = {}) {
    const buffer = sounds[name];
    if (!buffer || audio.state !== "running") return;
    const source = audio.createBufferSource(),
      volume = audio.createGain();
    source.buffer = buffer;
    volume.gain.value = gain;
    source.connect(volume).connect(audio.destination);
    source.start(audio.currentTime + delay, offset);
  }

  let done = false;
  function exit() {
    if (done) return;
    done = true;
    if (!reduced) playSound("whoosh-out", { offset: 0.2, gain: 0.45 });
    const duration = reduced ? 250 : 450;
    stage.animate([{ transform: "scale(1)" }, { transform: "scale(1.06)" }], {
      duration,
      easing: "ease-in",
      fill: "forwards",
    });
    splash
      .animate([{ opacity: 1 }, { opacity: 0 }], { duration, fill: "forwards" })
      .finished.then(() => {
        splash.remove();
        root.classList.remove("splash-lock");
        setTimeout(() => audio?.close().catch(() => {}), 1500);
      });
  }

  function spawn(className, x, y) {
    const el = document.createElement("i");
    el.className = className;
    el.style.left = x + "px";
    el.style.top = y + "px";
    splash.append(el);
    return el;
  }

  // Brasas que se desprenden del martillo mientras vuela.
  function shedEmbers(until) {
    const tick = () => {
      if (performance.now() > until || done) return;
      const r = hammer.getBoundingClientRect(),
        ember = spawn(
          "splash-ember",
          r.left + r.width / 2 + (Math.random() - 0.5) * 24,
          r.top + r.height / 2 + (Math.random() - 0.5) * 24,
        ),
        drift = (Math.random() - 0.5) * 30;
      ember
        .animate(
          [
            { transform: "translate(-50%, -50%) scale(1)", opacity: 1 },
            {
              transform: `translate(calc(-50% + ${drift}px), calc(-50% + ${20 + Math.random() * 30}px)) scale(0.3)`,
              opacity: 0,
            },
          ],
          { duration: 500 + Math.random() * 300, easing: "ease-out" },
        )
        .finished.then(() => ember.remove());
      setTimeout(tick, 35);
    };
    tick();
  }

  // Chispas con gravedad que saltan hacia arriba desde el suelo.
  function burst(x, y) {
    const flash = spawn("splash-flash", x, y);
    flash
      .animate(
        [
          { transform: "scale(0.2)", opacity: 1 },
          { transform: "scale(1.6)", opacity: 0 },
        ],
        { duration: 380, easing: "ease-out", fill: "forwards" },
      )
      .finished.then(() => flash.remove());
    const shock = spawn("splash-shock", x, y);
    shock
      .animate(
        [
          { transform: "scale(0.1)", opacity: 1 },
          { transform: "scale(1.5)", opacity: 0 },
        ],
        { duration: 520, easing: "cubic-bezier(.1,.7,.3,1)", fill: "forwards" },
      )
      .finished.then(() => shock.remove());
    for (let i = 0; i < 34; i++) {
      const spark = spawn("splash-spark", x, y),
        angle = ((Math.random() - 0.5) * 170 * Math.PI) / 180,
        speed = 130 + Math.random() * 190,
        gravity = 260,
        vx = speed * Math.sin(angle),
        vy = -speed * Math.cos(angle),
        frames = [];
      spark.style.height = 6 + Math.random() * 12 + "px";
      for (let s = 0; s <= 4; s++) {
        const t = s / 4,
          heading = (Math.atan2(vx, -(vy + gravity * t)) * 180) / Math.PI;
        frames.push({
          transform: `translate(-50%, -50%) translate(${vx * t}px, ${vy * t + (gravity * t * t) / 2}px) rotate(${heading}deg) scaleY(${1 - 0.7 * t})`,
          opacity: 1 - t * t,
        });
      }
      spark
        .animate(frames, {
          duration: 520 + Math.random() * 380,
          easing: "cubic-bezier(.2,.6,.4,1)",
          fill: "forwards",
        })
        .finished.then(() => spark.remove());
    }
  }

  // Impact frame: un par de fotogramas con los colores invertidos.
  function impactFrame() {
    const styles = ["is-impact-a", "is-impact-b", "is-impact-a"];
    styles.forEach((style, i) =>
      setTimeout(() => {
        splash.classList.remove(...styles);
        splash.classList.add(style);
      }, i * 65),
    );
    setTimeout(() => splash.classList.remove(...styles), styles.length * 65);
  }

  // Cada letra nace en el punto del golpe y vuela a su lugar.
  function reveal(x, y) {
    letters.forEach((letter, i) => {
      const r = letter.getBoundingClientRect(),
        dx = x - (r.left + r.width / 2),
        dy = y - (r.top + r.height / 2);
      letter.animate(
        [
          {
            transform: `translate(${dx}px, ${dy}px) scale(0.2)`,
            opacity: 0,
            filter: "blur(6px)",
            color: "#ffda00",
          },
          {
            transform: "none",
            opacity: 1,
            filter: "blur(0)",
            color: getComputedStyle(letter).color,
          },
        ],
        {
          duration: 560,
          delay: 160 + i * 45,
          easing: "cubic-bezier(.2,.9,.25,1.12)",
          fill: "both",
        },
      );
    });
  }

  async function play() {
    const logo = hammer.querySelector("img"),
      ready = Promise.race([
        Promise.all([
          logo.decode().catch(() => {}),
          document.fonts.ready,
          loadSounds,
        ]),
        wait(1500),
      ]);
    await entered;
    await gate.animate(
      [
        { opacity: 1, transform: "scale(1)" },
        { opacity: 0, transform: "scale(0.92)" },
      ],
      { duration: 250, easing: "ease-in", fill: "forwards" },
    ).finished;
    gate.remove();
    await ready;
    if (done) return;
    if (reduced) {
      hammer.style.opacity = 1;
      letters.forEach((l) => (l.style.opacity = 1));
      await wait(800);
      return exit();
    }

    // Arco (cuarto de elipse) desde fuera de la esquina inferior derecha.
    const box = hammer.getBoundingClientRect(),
      cx = box.left + box.width / 2,
      cy = box.top + box.height / 2,
      dx = innerWidth + box.width / 2 - cx,
      dy = innerHeight + box.height / 2 - cy,
      steps = 24,
      frames = [];
    for (let i = 0; i <= steps; i++) {
      const p = i / steps,
        t = (p * Math.PI) / 2;
      frames.push({
        transform: `translate(${dx * Math.cos(t)}px, ${dy * (1 - Math.sin(t))}px) rotate(${-720 * (1 - p)}deg) scale(${0.55 + 0.45 * p})`,
        opacity: 1,
      });
    }

    // Estela tipo cometa: tres trazos de distinto largo con la cabeza en el
    // martillo; al llegar, la cola se recoge hacia él.
    const gradient = trail.querySelector("linearGradient");
    trail.setAttribute("viewBox", `0 0 ${innerWidth} ${innerHeight}`);
    Object.entries({ x1: cx + dx, y1: cy + dy, x2: cx, y2: cy }).forEach(
      ([k, v]) => gradient.setAttribute(k, v),
    );
    const flight = {
      duration: 850,
      easing: "cubic-bezier(.25,.8,.3,1)",
      fill: "forwards",
    };
    const tails = { glow: 0.42, body: 0.26, core: 0.12 };
    trail.querySelectorAll("path").forEach((path) => {
      const tail = tails[path.getAttribute("class")];
      path.setAttribute(
        "d",
        `M ${cx + dx} ${cy + dy} A ${dx} ${dy} 0 0 0 ${cx} ${cy}`,
      );
      path.style.strokeDasharray = `${tail} 3`;
      path
        .animate(
          [{ strokeDashoffset: tail }, { strokeDashoffset: tail - 1 }],
          flight,
        )
        .finished.then(() =>
          path.animate(
            [{ strokeDashoffset: tail - 1 }, { strokeDashoffset: -1 }],
            { duration: 240, easing: "ease-out", fill: "forwards" },
          ),
        );
    });
    shedEmbers(performance.now() + flight.duration - 80);
    floor.animate(
      [
        { opacity: 0, transform: "scaleX(0)" },
        { opacity: 1, transform: "scaleX(1)" },
      ],
      { duration: 500, delay: 550, easing: "ease-out", fill: "forwards" },
    );
    await hammer.animate(frames, flight).finished;

    // Martillazo contra el suelo: se echa hacia atrás y cae acostado,
    // con la cara de golpe apoyada sobre la línea del suelo.
    const back = "translate(0, -4%) rotate(22deg)",
      hit = "translate(92.7%, -22.4%) rotate(-90deg)";
    // El pico del whoosh está a 0,85 s del archivo; empezando en 0,47 s llega
    // al pico justo cuando cae el martillo (impulso + golpe = 390 ms).
    playSound("whoosh-in", { offset: 0.47, gain: 0.9 });
    await swing.animate([{ transform: "none" }, { transform: back }], {
      duration: 240,
      easing: "cubic-bezier(.3,0,.3,1)",
      fill: "forwards",
    }).finished;
    await swing.animate([{ transform: back }, { transform: hit }], {
      duration: 150,
      easing: "cubic-bezier(.7,0,1,1)",
      fill: "forwards",
    }).finished;
    if (done) return;
    // El golpe del archivo empieza a los 0,15 s: se salta el silencio inicial.
    playSound("smash", { offset: 0.15 });
    impactFrame();
    swing.animate(
      [
        { transform: hit },
        { transform: "translate(92.7%, -24%) rotate(-84deg)" },
        { transform: hit },
      ],
      { duration: 300, easing: "ease-out", fill: "forwards" },
    );
    stage.animate(
      [
        { transform: "translate(0, 0)" },
        { transform: "translate(-7px, 6px)" },
        { transform: "translate(6px, -4px)" },
        { transform: "translate(-3px, 2px)" },
        { transform: "translate(0, 0)" },
      ],
      { duration: 280 },
    );
    const spot = impact.getBoundingClientRect();
    burst(spot.left, spot.top);
    reveal(spot.left, spot.top);
    await wait(1250);
    exit();
  }
  play().catch(exit);
})();
