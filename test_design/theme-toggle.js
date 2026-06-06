// Shared light/dark toggle for the calm "test_design" mockups.
// Injects a floating button; default theme = dark; choice persisted per browser.
(function () {
  function apply(theme) {
    document.documentElement.classList.toggle('dark', theme === 'dark');
  }
  var saved = (function () { try { return localStorage.getItem('calmTheme'); } catch (e) { return null; } }) ()
    || 'dark';
  apply(saved);
  window.addEventListener('DOMContentLoaded', function () {
    var btn = document.createElement('button');
    btn.id = 'theme-toggle';
    function label() { btn.textContent = document.documentElement.classList.contains('dark') ? 'Thème clair' : 'Thème sombre'; }
    label();
    btn.addEventListener('click', function () {
      var dark = !document.documentElement.classList.contains('dark');
      apply(dark ? 'dark' : 'light');
      try { localStorage.setItem('calmTheme', dark ? 'dark' : 'light'); } catch (e) {}
      label();
    });
    document.body.appendChild(btn);
  });
})();
