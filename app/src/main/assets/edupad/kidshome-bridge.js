/* キッズホームに内蔵したときだけ動く橋渡し（ブラウザで開いたときは何もしない）。
   WebView には 共有（navigator.share）・ダウンロード・印刷 が無いので、アプリ側（KidsHome）に渡す。 */
(function () {
  var B = window.KidsHome;
  if (!B) return;

  function toBase64(blob, done) {
    var r = new FileReader();
    r.onload = function () { done(String(r.result).split(',')[1] || ''); };
    r.readAsDataURL(blob);
  }

  // 共有：ファイルを Android の共有シートへ
  navigator.canShare = function (d) { return !!(d && d.files && d.files.length); };
  navigator.share = function (d) {
    return new Promise(function (resolve) {
      var files = (d && d.files) || [];
      if (!files.length) { resolve(); return; }
      var f = files[0];
      toBase64(f, function (b64) { B.share(b64, f.name || 'file', f.type || 'application/octet-stream', (d && d.title) || ''); resolve(); });
    });
  };

  // <a download> のクリック：端末に保存（画像は「写真」、それ以外は「ダウンロード」）
  var click = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function () {
    if (this.hasAttribute('download') && this.href) {
      var name = this.getAttribute('download') || 'file';
      fetch(this.href).then(function (r) { return r.blob(); }).then(function (blob) {
        toBase64(blob, function (b64) { B.save(b64, name, blob.type || 'application/octet-stream'); });
      });
      return;
    }
    return click.apply(this, arguments);
  };

  // 印刷：ページ（プリントの iframe も）を Android の印刷へ
  window.print = function () { B.print(location.href); };
  document.addEventListener('load', function (e) {
    var t = e.target;
    if (t && t.tagName === 'IFRAME') {
      try { t.contentWindow.print = function () { B.print(t.src); }; } catch (_) {}
    }
  }, true);
})();
