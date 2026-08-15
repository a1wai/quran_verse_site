#!/usr/bin/env bash
#
# Render android/icon/nur-icon.svg into the legacy launcher PNGs.
#
# Only Android 7 and below use these — Android 8+ draws the adaptive icon in
# res/drawable instead — but they are what shows on older devices, so they are checked
# in rather than generated at build time.
#
# Needs a Chromium or Chrome binary. Set CHROME to point at one, or let the script find
# the usual suspects. The SVG is re-sized and rasterised on a canvas at each density, so
# every icon is drawn from the vectors rather than scaled up from one bitmap.
#
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
svg="$here/android/icon/nur-icon.svg"
res="$here/android/app/src/main/res"

chrome="${CHROME:-}"
if [ -z "$chrome" ]; then
  for candidate in /opt/pw-browsers/chromium google-chrome chromium chromium-browser; do
    if command -v "$candidate" >/dev/null 2>&1; then chrome="$(command -v "$candidate")"; break; fi
    if [ -x "$candidate" ]; then chrome="$candidate"; break; fi
  done
fi
[ -n "$chrome" ] || { echo "no chrome/chromium found; set CHROME=/path/to/chrome" >&2; exit 1; }

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

{
  printf '%s' '<!doctype html><meta charset="utf-8"><body><pre id="out"></pre><script>const SVG=atob("'
  base64 -w0 "$svg"
  cat <<'JS'
");
const sizes=[48,72,96,144,192,512];
function draw(n){
  return new Promise(function(done){
    const t=SVG.replace(/width="\d+" height="\d+"/,'width="'+n+'" height="'+n+'"');
    const img=new Image();
    img.onload=function(){
      const c=document.createElement('canvas'); c.width=c.height=n;
      c.getContext('2d').drawImage(img,0,0,n,n);
      done(n+' '+c.toDataURL('image/png').split(',')[1]);
    };
    img.src='data:image/svg+xml;base64,'+btoa(unescape(encodeURIComponent(t)));
  });
}
Promise.all(sizes.map(draw)).then(function(lines){
  document.getElementById('out').textContent=lines.join('\n');
});
JS
  printf '%s' '</script>'
} > "$work/page.html"

"$chrome" --headless --disable-gpu --no-sandbox --virtual-time-budget=10000 \
  --dump-dom "file://$work/page.html" > "$work/dom.html" 2>/dev/null

# Pull "<size> <base64>" out of the dumped DOM. The first and last lines share a line with
# the surrounding <pre> tags, so this matches inside the line rather than anchoring to it.
grep -oE '[0-9]+ [A-Za-z0-9+/=]{100,}' "$work/dom.html" > "$work/pngs.txt" || true
[ -s "$work/pngs.txt" ] || { echo "chrome produced nothing; is $chrome runnable?" >&2; exit 1; }

density_of() {
  case "$1" in
    48) echo mdpi ;; 72) echo hdpi ;; 96) echo xhdpi ;;
    144) echo xxhdpi ;; 192) echo xxxhdpi ;; *) echo "" ;;
  esac
}

echo "rendering launcher icons from ${svg#$here/}"
while read -r size data; do
  if [ "$size" = "512" ]; then
    printf '%s' "$data" | base64 -d > "$here/android/icon/nur-icon-512.png"
    echo "   512px  android/icon/nur-icon-512.png  (store listing)"
    continue
  fi
  dir="$res/mipmap-$(density_of "$size")"
  mkdir -p "$dir"
  printf '%s' "$data" | base64 -d > "$dir/ic_launcher.png"
  cp "$dir/ic_launcher.png" "$dir/ic_launcher_round.png"
  echo "  $(printf '%4s' "$size")px  ${dir#$here/}/ic_launcher{,_round}.png"
done < "$work/pngs.txt"
echo "done"
