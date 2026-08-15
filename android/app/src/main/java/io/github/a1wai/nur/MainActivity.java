package io.github.a1wai.nur;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

/**
 * Nur, as one screen.
 *
 * The whole application is index.html, shipped inside the APK and served to a WebView over a
 * virtual https origin so it behaves like a real page: a stable origin for localStorage,
 * ordinary CORS for the verse and tafsir requests, and a secure context.
 *
 * The window is fixed. No system bars, no rotation, no zoom, no overscroll, no scrollbars, no
 * selection handles, and no resize when the keyboard opens. What the page draws is what the
 * screen shows.
 *
 * Written without lambdas or method references on purpose: this is dexed by dx, which does not
 * understand invokedynamic.
 */
public class MainActivity extends Activity {

    /** Reserved host — it never resolves on the network, so every request lands in the interceptor. */
    private static final String ASSET_HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + ASSET_HOST + "/index.html";
    private static final String ASSET_DIR = "web";

    /** Matches --bg and theme-color in index.html, so the first frame is not a white flash. */
    private static final int NIGHT = 0xFF05070D;

    private WebView web;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        Window window = getWindow();
        window.setBackgroundDrawable(new ColorDrawable(NIGHT));
        goEdgeToEdge(window);

        web = new WebView(this);
        web.setBackgroundColor(NIGHT);
        configure(web);
        web.setWebViewClient(new NurWebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        setContentView(web);

        if (state == null) {
            web.loadUrl(START_URL);
        } else {
            web.restoreState(state);
        }
    }

    /* ── the window ───────────────────────────────────────────────────────────── */

    /**
     * Draw under the cutout and behind the hidden system bars. index.html already asks for
     * viewport-fit=cover and pads itself with env(safe-area-inset-*), so the page keeps its own
     * edges once WebView hands it the insets.
     */
    private void goEdgeToEdge(Window window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
        }
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        hideSystemBars();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    /* ── the web view ─────────────────────────────────────────────────────────── */

    private void configure(WebView view) {
        WebSettings s = view.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // favorites, settings, the deck, cached tafsir
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMediaPlaybackRequiresUserGesture(false);

        // Nothing here is a browser: no zoom, no reflow, no system font scaling.
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(false);
        s.setLoadWithOverviewMode(false);
        s.setTextZoom(100);

        // Assets arrive over https; nothing may be pulled off the disk directly.
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        // Fixed: no bounce, no rubber band, no scrollbars, no selection handles.
        view.setOverScrollMode(View.OVER_SCROLL_NEVER);
        view.setVerticalScrollBarEnabled(false);
        view.setHorizontalScrollBarEnabled(false);
        view.setLongClickable(false);
        view.setHapticFeedbackEnabled(false);
        view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return true;   // swallow it: no selection handles, no context menu
            }
        });

        CookieManager.getInstance().setAcceptCookie(false);
    }

    private class NurWebViewClient extends WebViewClient {

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri url = request.getUrl();
            if (!ASSET_HOST.equals(url.getHost())) return null;
            return serve(url.getPath());
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return openOutside(request.getUrl());
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return openOutside(Uri.parse(url));
        }
    }

    /** Anything that is not our own page is somebody else's; hand it to the browser. */
    private boolean openOutside(Uri url) {
        if (ASSET_HOST.equals(url.getHost())) return false;
        String scheme = url.getScheme();
        if (scheme == null) return true;
        if (!scheme.equals("http") && !scheme.equals("https") && !scheme.equals("mailto")) return true;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, url)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException ignored) {
            // No browser on the device: stay where we are rather than crash.
        }
        return true;
    }

    /** Called off the UI thread, once per request the page makes to its own origin. */
    private WebResourceResponse serve(String path) {
        if (path == null || path.length() <= 1) path = "/index.html";
        if (path.contains("..")) return notFound();
        AssetManager assets = getAssets();
        try {
            InputStream in = assets.open(ASSET_DIR + path);
            WebResourceResponse response = new WebResourceResponse(mimeOf(path), "utf-8", in);
            response.setResponseHeaders(headers());
            return response;
        } catch (IOException missing) {
            return notFound();
        }
    }

    private Map<String, String> headers() {
        return Collections.singletonMap("Cache-Control", "no-cache");
    }

    private WebResourceResponse notFound() {
        WebResourceResponse r = new WebResourceResponse(
                "text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
        r.setStatusCodeAndReasonPhrase(404, "Not Found");
        r.setResponseHeaders(headers());
        return r;
    }

    private static String mimeOf(String path) {
        String p = path.toLowerCase();
        if (p.endsWith(".html") || p.endsWith(".htm")) return "text/html";
        if (p.endsWith(".js")) return "text/javascript";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".json")) return "application/json";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".webp")) return "image/webp";
        if (p.endsWith(".woff2")) return "font/woff2";
        if (p.endsWith(".woff")) return "font/woff";
        if (p.endsWith(".ttf")) return "font/ttf";
        return "application/octet-stream";
    }

    /* ── back ─────────────────────────────────────────────────────────────────── */

    /**
     * Back closes whatever the page has open, which is exactly what Escape does on a keyboard.
     * With nothing open it leaves the app without tearing it down, so coming back returns to the
     * same verse rather than dealing a new one.
     */
    private static final String ASK_PAGE_TO_CLOSE =
            "(function(){try{"
                    + "if(!document.querySelector('.sheet.on,.panel.on,#bloom.open'))return 'exit';"
                    + "document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));"
                    + "return 'closed';"
                    + "}catch(e){return 'exit'}})()";

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web == null) {
            super.onBackPressed();
            return;
        }
        web.evaluateJavascript(ASK_PAGE_TO_CLOSE, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                if (value == null || value.indexOf("exit") >= 0) moveTaskToBack(true);
            }
        });
    }

    /* ── lifecycle ────────────────────────────────────────────────────────────── */

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (web != null) web.saveState(out);
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
        hideSystemBars();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.setWebChromeClient(null);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
