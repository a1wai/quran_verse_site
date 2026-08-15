# Nur — a verse for where you are

One verse of the Qur'an at a time, matched to how you feel. The whole thing is a
single file: `index.html`. No build step, no dependencies, no server, no account.
Open it from a disk or drop it on any static host and it works.

---

## The files

| File | What it is |
|---|---|
| `index.html` | The entire application — markup, styles, verse data, stories, scene engine, everything. |
| `README.md` | This file. |
| `.gitattributes` | Line-ending settings for the repository. |
| `android/` | The Android app: a single activity that puts `index.html` on the screen, and the launcher icon. |
| `tools/` | The APK build, which needs a JDK and nothing else. |
| `dist/` | The built APK. |

To deploy: put `index.html` where a web server can see it. That is the whole
procedure. GitHub Pages, Netlify, a folder on a phone — all the same.

---

## What is in it

**377 chosen verses**, read and tagged one by one by mood, in the Rowwad
Translation Center English edition, written into the page so the app works with
no connection at all.

**63 events** — short retellings of Qur'anic accounts (the menu calls them
Events), each one carrying the key verse it turns on, so an explanation can be
opened for an event the same way it can for a verse.

**Whole Qur'an mode** — all 6,236 verses, fetched once from the open dataset at
[fawazahmed0/quran-api](https://github.com/fawazahmed0/quran-api).

**98 languages** for the verse itself, every one a published scholarly
translation, never machine translation.

**238 scenes**, drawn live on a canvas — nothing here is an image. 82 named
groups of drawing layers across 24 palettes. The scene is chosen from what the
verse actually pictures: a verse that names the sea gets waves, one that names a
spider gets a web, one that names the scales gets a balance. Across the 377
chosen verses alone that works out to 101 distinct scenes, and the whole-Qur'an
pool reaches all 238.

---

## How the next verse is chosen

Not a dice roll, and no longer a plain shuffle bag:

- every verse in the current pool is dealt once before any of them comes back,
  and the deck reshuffles for each new round;
- the deck and a memory of the last 140 things you were shown are written to
  your device, so closing the page and coming back carries on where you were
  instead of starting the same handful of verses over;
- a card goes back under the deck instead of being dealt if it repeats the surah
  you just read, the scene you are still looking at, or anything in recent
  memory.

The effect is that you rarely see the same verse twice, and almost never see the
same sky twice in a row.

---

## Explanations

Swipe up on a verse (or press ↑, or scroll up, or choose Explain in the menu)
and the page blurs and a sheet slides up with a published tafsir for that verse. Swipe back down — or drag the
sheet down, or press ↓, or Escape — and it slides away.

- The short reading is **Al-Mukhtasar fi Tafsir al-Qur'an al-Karim**, the
  abridged commentary by the Tafsir Center for Quranic Studies, which is a few
  sentences per verse by design.
- Under it, **a fuller commentary** can be opened — Tafsir Ibn Kathir in English,
  and the equivalent edition where a language has one.
- Both come from [spa5k/tafsir_api](https://github.com/spa5k/tafsir_api) (sourced
  from quran.com) as plain static JSON, over three mirrors, with a nine-second
  timeout each. **Free, keyless, no account, nothing sent anywhere.**
- Every verse you have read is cached on the device, so it reopens instantly and
  offline.

There is no AI API in this project. There is no API key field, and any key,
cached machine translation or cached story translation left over from an earlier
version is deleted from the device on load.

---

## Languages

Choosing a language changes the whole page — the verse, the menus, the moods,
the settings, the About text and the events — and the layout flips to
right-to-left for Arabic, Urdu, Persian, Pashto, Uyghur, Kurdish, Sindhi, Hebrew
and Dhivehi.

Two different things are happening, and the difference matters:

- **The verse** is always a published human translation by scholars. It is never
  machine translated.
- **The page around it** — menus, moods, settings, About, and the event
  retellings — is machine translated once, by a free keyless service, and then
  kept on the device. Coming back to a language you have used before is instant
  and works offline.

An event is never shown in English and then swapped underneath you: it is only
painted once its own language is in hand. To keep that from being a wait, the
whole set is fetched quietly in the background the moment a language is chosen,
and the next card in the deck is always fetched before you can reach it.

The interface translation tries Google's public `translate_a` endpoint first,
falls back to MyMemory, and falls back again to per-string requests. **If all of
them are unreachable or blocked, the verse still changes language and the menus
stay in English**, with a note in the Language panel saying so. This is the one
part of the app that depends on a third-party service that could change without
warning; the verse, the explanation and everything else do not.

The language you last read in, and the verse edition, are remembered and restored
next time you open the page.

---

## Gestures

| Action | What happens |
|---|---|
| Tap anywhere | The next verse |
| Double tap | Save to favorites (with a small confirmation) |
| Swipe up | Explanation slides up, background blurs |
| Swipe down | Explanation slides away |
| The glowing dot | The menu — everything behind it blurs. Explain is in there too |
| → or Space | Next verse |
| ↑ / ↓ | Open / close the explanation |
| L | Save to favorites |
| Escape | Close anything open |

---

## Settings

Theme (dark / light / auto), four typefaces, three text sizes, moving scenes on
or off, scene drawn from the verse or one calm night sky, verse reference on or
off, chosen verses or the whole Qur'an, explanation length (short or full), and
clearing your favorites.

Everything you keep — favorites, settings, cached explanations, cached interface
translations, your deck and your recent memory — is stored on your device only,
in `localStorage`, under keys beginning `nur.`. Nothing is sent anywhere and
there is no analytics of any kind.

---

## The Android app

`dist/nur-1.0.0.apk` is the whole thing as an installable app. The page is inside
the APK, so it opens with no connection at all; the network is only ever used for
the things that were already fetched — whole-Qur'an mode, the explanations, the
interface translations, the fonts.

Install it by copying it to the phone and opening it, allowing "install unknown
apps" for whatever app you opened it from. **Android 7.0 or newer.**

**One screen, and it stays put.** No status bar, no navigation bar, no address
bar, no rotation, no pinch zoom, no scrollbars, no rubber-band at the edges, no
long-press selection, and no layout jump when the keyboard opens. The verse does
not move because something behind it moved. Panels and the explanation sheet still
scroll inside themselves — a tafsir and a list of 98 languages have to — but
nothing else does, and nothing scrolls the page underneath.

Back closes whatever is open, exactly as Escape does on a keyboard. With nothing
open it leaves the app without tearing it down, so coming back returns the verse
you left rather than dealing a new one.

The page is served to the WebView from inside the APK over a virtual
`https://appassets.androidplatform.net` origin rather than a `file://` path. That
gives it a real origin: `localStorage` that persists properly, ordinary CORS for
the verse and tafsir requests, and a secure context. Nothing on the device's disk
is reachable from the page.

**The icon** is a niche with a lamp in it — Ayat an-Nur, three shapes. It ships as
an adaptive icon (with a themed monochrome layer for Android 13 and up) drawn from
vectors, plus PNGs at five densities for older launchers.
`android/icon/nur-icon.svg` is the source; `tools/render-icons.sh` redraws the
PNGs from it.

### Building it

```sh
tools/build-apk.sh
```

A JDK 21 or newer, Python 3, and nothing else — **no Android SDK, no Gradle, no
Android Studio**. The build fetches its own toolchain from Maven Central and caches
it under `~/.cache/nur-android`:

| Piece | Where it comes from |
|---|---|
| `aapt2` | `org.apktool:apktool-lib`, which ships the prebuilt binaries for Linux, macOS and Windows |
| `dx` | `com.jakewharton.android.repackaged:dalvik-dx` |
| `apksigner` | `com.android.tools.build:apksig`, driven by `tools/ApkSign.java` |
| `android.jar` | `org.robolectric:android-all`, which doubles as aapt2's framework because it carries `resources.arsc` as well as the classes |
| `zipalign` | `tools/zipalign.py` |

Every download is checked against the `.sha1` Maven publishes beside it. The first
build pulls about 200 MB (nearly all of it the framework jar) and later ones pull
nothing.

The APK is signed with an APK Signature Scheme v2 signature — hence Android 7.0,
the first release that reads one. The key is made on first build at
`android/keystore/nur.p12` and is deliberately **not** in the repository. Keep it:
Android will only install an update over an app signed with the same key. To sign
with your own instead, point `NUR_KEYSTORE`, `NUR_KEYSTORE_PASS` and
`NUR_KEYSTORE_ALIAS` wherever you like.

`.github/workflows/android.yml` runs the same script on every push that touches
`index.html` or the app, and leaves the APK as a build artifact.

---

## Sources

- Verses: [quranenc.com](https://quranenc.com) (Rowwad Translation Center),
  via [fawazahmed0/quran-api](https://github.com/fawazahmed0/quran-api) —
  490 translations across 98 languages, sourced from quranenc.com, tanzil.net
  and the King Fahd Complex.
- Tafsir: [spa5k/tafsir_api](https://github.com/spa5k/tafsir_api), sourced from
  quran.com.
- Fonts: Google Fonts (Spectral, Jost, and the optional typeface packs).

Whatever good you find in this is from Allah; whatever error, from us.
