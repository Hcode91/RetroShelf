(function () {
  'use strict';
  if (window.__retroShelfReader) return;
  let enabled = false, dark = true, timer = null;
  const own = 'rs-reader-';
  const styleText = `
    body.rs-reader-on { background:#090e1b!important;color:#f2f6ff!important;margin:0!important;padding:12px!important; }
    html[data-rs-reader-theme=light] body.rs-reader-on { background:#faf8ff!important;color:#201d29!important; }
    body.rs-reader-on .rs-reader-path { display:block!important;float:none!important;width:100%!important;max-width:none!important;margin:0!important;padding:0!important;min-height:0!important;background:transparent!important;border:0!important;color:inherit!important; }
    body.rs-reader-on > :not(.rs-reader-path):not(.rs-reader-entry):not(.rs-reader-control):not(.rs-reader-name):not(.rs-reader-picture):not(style):not(script),
    body.rs-reader-on .rs-reader-path > :not(.rs-reader-path):not(.rs-reader-entry):not(.rs-reader-control):not(.rs-reader-name):not(.rs-reader-picture):not(style):not(script) { display:none!important; }
    body.rs-reader-on .rs-reader-entry { display:block!important;background:#1b2438!important;border-radius:14px!important;padding:14px!important;margin:0 0 12px!important;list-style:none!important;border:0!important;width:100%!important;box-sizing:border-box!important;color:inherit!important; }
    html[data-rs-reader-theme=light] body.rs-reader-on .rs-reader-entry { background:#eee8fa!important; }
    body.rs-reader-on .rs-reader-entry:not(.rs-reader-list) > :not(.rs-reader-path):not(.rs-reader-name):not(.rs-reader-picture) { display:none!important; }
    body.rs-reader-on .rs-reader-entry.rs-reader-list > :not(.rs-reader-name):not(img):not(picture):not(td),
    body.rs-reader-on .rs-reader-entry td > :not(.rs-reader-name):not(img):not(picture) { display:none!important; }
    body.rs-reader-on .rs-reader-entry.rs-reader-list > td:not(:first-child) { display:none!important; }
    body.rs-reader-on .rs-reader-entry td { display:block!important;border:0!important;padding:0!important;background:transparent!important;color:inherit!important; }
    body.rs-reader-on .rs-reader-name { color:inherit!important;font-size:17px!important;font-weight:650!important;line-height:1.4!important;display:block!important;text-decoration:none!important;white-space:normal!important; }
    body.rs-reader-on .rs-reader-name img, body.rs-reader-on .rs-reader-entry > img, body.rs-reader-on .rs-reader-picture { width:auto!important;max-width:100%!important;max-height:260px!important;object-fit:contain!important;display:block!important;margin:0 auto 10px!important; }
    body.rs-reader-on .rs-reader-control { display:block!important;margin:10px 0!important; }
  `;
  function visible(el) { return el && el.getClientRects().length > 0 && getComputedStyle(el).visibility !== 'hidden'; }
  function path(el) {
    for (let p = el.parentElement; p && p !== document.body; p = p.parentElement) if (!p.classList.contains(own + 'path')) p.classList.add(own + 'path');
  }
  function mark(el, kind) { if (!el.classList.contains(own + kind)) el.classList.add(own + kind); path(el); }
  function original() { document.body && document.body.classList.remove(own + 'on'); return 'original'; }
  function apply() {
    if (!enabled || !document.body) return original();
    document.body.classList.remove(own + 'on');
    // Check the original layout so a newly inserted gate cannot be hidden by reader CSS.
    const gates = document.querySelectorAll('#challenge-form,#cf-challenge-running,iframe[src*="challenges.cloudflare.com"],input[type="password"],[role="dialog"],[aria-modal="true"],.paywall,#paywall,[class*="adblock-overlay"],[id*="adblock-overlay"],[class*="consent-overlay"]');
    if (Array.from(gates).some(visible)) return original();
    const host = location.hostname.replace(/^www\./, '');
    let entries = [];
    if (host === 'steamrip.com') {
      entries = Array.from(document.querySelectorAll('.az-list-item')).filter(el => el.querySelector('a[href]'));
      entries.forEach(el => {
        mark(el, 'entry'); el.classList.add(own + 'list');
        const name = el.querySelector('a[href]'); name.classList.add(own + 'name');
      });
      if (!entries.length && (location.pathname === '/' || /^\/page\//.test(location.pathname) || /^\/category\//.test(location.pathname))) {
        const cards = Array.from(document.querySelectorAll('.post-item,article')).filter(el => el.querySelector('.post-title a,.entry-title a'));
        cards.forEach(el => {
          const title = el.querySelector('.post-title,.entry-title');
          const image = el.querySelector('picture,img') || el.querySelector('.post-thumb,.post-thumbnail');
          mark(el, 'entry'); mark(title, 'name'); if(image) mark(image, 'picture');
        });
        entries = cards;
      }
    } else if (host === 'nopaystation.com') {
      entries = Array.from(document.querySelectorAll('.resultsTable > tbody > tr')).filter(el => el.querySelector('td > a[href^="/view/"]'));
      entries.forEach(el => {
        mark(el, 'entry'); el.classList.add(own + 'list');
        el.querySelector('td > a[href^="/view/"]').classList.add(own + 'name');
      });
    }
    if (!entries.length) return original();
    document.querySelectorAll('.pagination,.pages-numbers').forEach(el => { if (!el.classList.contains('az-link-links-block')) mark(el, 'control'); });
    const sheet = document.getElementById(own + 'style');
    if (!sheet) { const style = document.createElement('style'); style.id = own + 'style'; style.textContent = styleText; document.head.appendChild(style); }
    if (!document.getElementById(own + 'style').sheet) return original();
    document.documentElement.setAttribute('data-rs-reader-theme', dark ? 'dark' : 'light');
    document.body.classList.add(own + 'on');
    return 'reader';
  }
  document.addEventListener('click', function (event) {
    if (!enabled || location.hostname.replace(/^www\./,'') !== 'nopaystation.com') return;
    const link = event.target.closest && event.target.closest('a.rs-reader-name');
    if (!link || !document.body.classList.contains(own + 'on')) return;
    const target = new URL(link.href, location.href);
    if (target.origin !== location.origin || !target.pathname.startsWith('/view/')) return;
    event.preventDefault(); event.stopImmediatePropagation();
    location.assign(target.href); // User click opens the ordinary article, without exporting its contents.
  }, true);
  new MutationObserver(function () {
    if (!enabled || timer !== null) return;
    timer = setTimeout(function () { timer = null; apply(); }, 250);
  }).observe(document.documentElement, { childList:true, subtree:true });
  window.__retroShelfReader = { setEnabled:function(value,theme) { enabled = value === true; dark = theme !== false; return apply(); } };
}());
