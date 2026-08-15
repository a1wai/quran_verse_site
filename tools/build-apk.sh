#!/usr/bin/env bash
#
# Build the Nur APK without installing the Android SDK.
#
# Everything the build needs is either a JDK (21 or newer) or a jar on Maven Central:
#
#   aapt2                  from org.apktool:apktool-lib, which ships the prebuilt binaries
#   dx                     from com.jakewharton.android.repackaged:dalvik-dx
#   apksigner              from com.android.tools.build:apksig, driven by tools/ApkSign.java
#   android.jar            from org.robolectric:android-all, which doubles as aapt2's -I
#                          framework because it carries resources.arsc as well as the classes
#   zipalign               tools/zipalign.py
#
# The downloads are cached under ~/.cache/nur-android (override with NUR_BUILD_CACHE) and
# each one is checked against the .sha1 Maven publishes next to it.
#
#   tools/build-apk.sh              build dist/nur-<version>.apk
#   tools/build-apk.sh --clean      throw away build/ first
#
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cache="${NUR_BUILD_CACHE:-$HOME/.cache/nur-android}"
build="$root/build"
dist="$root/dist"
app="$root/android/app/src/main"

version_name="${NUR_VERSION_NAME:-1.0.0}"
version_code="${NUR_VERSION_CODE:-1}"
min_sdk=24
target_sdk=35

apktool_version=3.0.3
dx_version=16.0.1
apksig_version=2.3.0
android_all=15-robolectric-13954326       # the API 35 framework

keystore="${NUR_KEYSTORE:-$root/android/keystore/nur.p12}"
keystore_pass="${NUR_KEYSTORE_PASS:-nur-signing-key}"
keystore_alias="${NUR_KEYSTORE_ALIAS:-nur}"

central=https://repo1.maven.org/maven2

say() { printf '\033[1m▸\033[0m %s\n' "$*"; }
die() { printf '\033[31m✗ %s\033[0m\n' "$*" >&2; exit 1; }

[ "${1:-}" = "--clean" ] && rm -rf "$build"

command -v java   >/dev/null || die "java not found (JDK 21+ required)"
command -v javac  >/dev/null || die "javac not found (a JDK, not just a JRE, is required)"
command -v python3 >/dev/null || die "python3 not found"
command -v keytool >/dev/null || die "keytool not found"

# ── the toolchain ────────────────────────────────────────────────────────────────

mkdir -p "$cache"

fetch() {   # fetch <maven-path> <local-name>
  local path="$1" name="$2" dest="$cache/$2"
  [ -f "$dest" ] && { echo "$dest"; return; }
  say "fetching $name" >&2
  curl -fsSL --retry 3 --retry-delay 2 -o "$dest.part" "$central/$path"
  local want got
  want="$(curl -fsSL --retry 3 "$central/$path.sha1" | tr -d '[:space:]' | cut -c1-40)"
  got="$(sha1sum "$dest.part" | cut -d' ' -f1)"
  [ "$want" = "$got" ] || { rm -f "$dest.part"; die "$name failed its checksum ($got != $want)"; }
  mv "$dest.part" "$dest"
  echo "$dest"
}

apktool_jar="$(fetch "org/apktool/apktool-lib/$apktool_version/apktool-lib-$apktool_version.jar" "apktool-lib-$apktool_version.jar")"
dx_jar="$(fetch "com/jakewharton/android/repackaged/dalvik-dx/$dx_version/dalvik-dx-$dx_version.jar" "dalvik-dx-$dx_version.jar")"
apksig_jar="$(fetch "com/android/tools/build/apksig/$apksig_version/apksig-$apksig_version.jar" "apksig-$apksig_version.jar")"
say "fetching the API 35 framework (once; it is large)"
framework_jar="$(fetch "org/robolectric/android-all/$android_all/android-all-$android_all.jar" "android-$android_all.jar")"

# aapt2 lives inside the apktool jar, one binary per host platform.
case "$(uname -s)" in
  Linux)  aapt2_entry=prebuilt/linux/aapt2 ;;
  Darwin) aapt2_entry=prebuilt/macosx/aapt2 ;;
  *)      aapt2_entry=prebuilt/windows/aapt2.exe ;;
esac
aapt2="$cache/aapt2"
if [ ! -x "$aapt2" ]; then
  say "unpacking aapt2"
  ( cd "$cache" && rm -rf prebuilt && unzip -q -o "$apktool_jar" "$aapt2_entry" \
      && mv "$aapt2_entry" aapt2 && rm -rf prebuilt )
  chmod +x "$aapt2"
fi
"$aapt2" version >/dev/null || die "the bundled aapt2 will not run on this machine"

# ── the page becomes an asset ────────────────────────────────────────────────────

say "staging index.html as an asset"
rm -rf "$app/assets"
mkdir -p "$app/assets/web"
cp "$root/index.html" "$app/assets/web/index.html"

# ── resources ────────────────────────────────────────────────────────────────────

rm -rf "$build"
mkdir -p "$build" "$dist"

say "compiling resources"
"$aapt2" compile --dir "$app/res" -o "$build/res.zip"

say "linking $version_name ($version_code)"
"$aapt2" link \
  -o "$build/base.apk" \
  -I "$framework_jar" \
  --manifest "$app/AndroidManifest.xml" \
  -A "$app/assets" \
  --min-sdk-version "$min_sdk" \
  --target-sdk-version "$target_sdk" \
  --version-code "$version_code" \
  --version-name "$version_name" \
  --no-version-vectors \
  "$build/res.zip"

# ── code ─────────────────────────────────────────────────────────────────────────

say "compiling java"
mkdir -p "$build/classes"
# --release 8 keeps invokedynamic out of the class files, which dx predates: no lambdas, and
# string concatenation compiled the old way. It also supplies java.*, which the framework jar
# leaves out — that jar carries android.* only, and goes on the classpath.
javac -nowarn -Xlint:-options -encoding UTF-8 \
  --release 8 \
  -classpath "$framework_jar" \
  -d "$build/classes" \
  $(find "$app/java" -name '*.java')

say "dexing"
java -cp "$dx_jar" com.android.dx.command.Main \
  --dex --min-sdk-version="$min_sdk" --output="$build/classes.dex" "$build/classes"

python3 - "$build/base.apk" "$build/classes.dex" <<'PY'
import sys, zipfile
apk, dex = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk, "a", zipfile.ZIP_DEFLATED) as z:
    z.write(dex, "classes.dex")
PY

# ── signing key ──────────────────────────────────────────────────────────────────

if [ ! -f "$keystore" ]; then
  say "creating a signing key at ${keystore#$root/}"
  mkdir -p "$(dirname "$keystore")"
  keytool -genkeypair -v \
    -keystore "$keystore" -storetype PKCS12 \
    -storepass "$keystore_pass" -keypass "$keystore_pass" \
    -alias "$keystore_alias" \
    -keyalg RSA -keysize 4096 -validity 10950 \
    -dname "CN=Nur, OU=Nur, O=Nur, C=US" >/dev/null 2>&1
  echo "  keep this file. Android will only accept an update signed with the same key."
fi

say "building the signer"
mkdir -p "$build/signer"
javac -nowarn -encoding UTF-8 -classpath "$apksig_jar" -d "$build/signer" "$root/tools/ApkSign.java"
signer_cp="$build/signer:$apksig_jar"
# apksig 2.3.0 predates the module system and reaches into sun.security.*, which JDK 9+
# keeps to itself unless asked. Only the v1 signer needs those, and v1 is off — see below —
# but the class initialiser still touches them, so the exports stay.
signer_opens=(--add-exports java.base/sun.security.x509=ALL-UNNAMED
              --add-exports java.base/sun.security.pkcs=ALL-UNNAMED)

# ── align, sign, verify ──────────────────────────────────────────────────────────

# Aligning before signing and not after is the whole point: a v2 signature covers the file
# byte for byte, so nothing may move once it is applied. Signing v2 and not v1 is what makes
# that safe — a v2 signature is one block spliced in ahead of the central directory, and it
# leaves every entry exactly where alignment put it, whereas v1 adds entries of its own and
# shifts everything after them. It is also the only scheme available here: the v1 signer in
# apksig 2.3.0 calls into sun.security internals that JDK 21 no longer has. Hence minSdk 24 —
# Android 7.0, the first release that reads v2.
say "aligning"
python3 "$root/tools/zipalign.py" "$build/base.apk" "$build/aligned.apk"

out="$dist/nur-$version_name.apk"
say "signing"
java "${signer_opens[@]}" -cp "$signer_cp" ApkSign sign \
  "$keystore" "$keystore_pass" "$keystore_alias" "$keystore_pass" \
  "$build/aligned.apk" "$out" "$min_sdk" false true

say "verifying"
python3 "$root/tools/zipalign.py" --check "$out" \
  || die "signing knocked the entries out of alignment"
java "${signer_opens[@]}" -cp "$signer_cp" ApkSign verify "$out" "$min_sdk"

echo
"$aapt2" dump badging "$out" 2>/dev/null | grep -E "^(package|application-label|sdkVersion|targetSdkVersion|launchable-activity)" || true
echo
say "$(du -h "$out" | cut -f1)  ${out#$root/}"
