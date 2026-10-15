"use strict";
// Carrusel del hero: avanza solo con fundido entre imágenes. Va en su propio
// archivo para que un error en app.js no lo detenga.
(() => {
  const slides = document.querySelectorAll(".hero-slide");
  if (slides.length < 2) return;
  let current = 0;
  setInterval(() => {
    if (document.hidden) return;
    slides[current].classList.remove("active");
    current = (current + 1) % slides.length;
    slides[current].classList.add("active");
  }, 5000);
})();
