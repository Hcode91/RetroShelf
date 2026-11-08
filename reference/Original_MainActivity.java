package io.retroshelf.poc;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.InputStream;
import java.net.URL;
import java.util.Locale;

/**
 * Tiny Android host for the standalone HTML proof of concept.
 * It serves bundled HTML on an HTTPS-like local app origin and uses the system
 * download manager for a file only after a tap on the Download button.
 */
public class MainActivity extends Activity {
    private static final String APP_HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://appassets.androidplatform.net/assets/index.html";
    private WebView view;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xff090e1b);
        getWindow().setNavigationBarColor(0xff090e1b);
        view = new WebView(this);
        setContentView(view);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        view.addJavascriptInterface(new SourceBridge(), "RetroShelfBridge");
        view.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView webView, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equalsIgnoreCase(uri.getScheme()) && APP_HOST.equalsIgnoreCase(uri.getHost())
                        && "/assets/index.html".equals(uri.getPath())) {
                    try {
                        InputStream html = getAssets().open("index.html");
                        return new WebResourceResponse("text/html", "UTF-8", html);
                    } catch (Exception ignored) {
                        return new WebResourceResponse("text/plain", "UTF-8", null);
                    }
                }
                return null;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView webView, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (APP_HOST.equalsIgnoreCase(uri.getHost())) return false;
                openExternal(uri.toString());
                return true;
            }
        });
        view.loadUrl(START_URL);
    }

    private static boolean allowed(String value) {
        try {
            URL u = new URL(value);
            String host = u.getHost().toLowerCase(Locale.ROOT);
            return "https".equalsIgnoreCase(u.getProtocol())
                    && !host.isEmpty() && !host.equals("localhost")
                    && !host.endsWith(".localhost") && !host.startsWith("127.");
        } catch (Exception e) { return false; }
    }

    private void openExternal(String url) {
        if (!allowed(url)) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { Toast.makeText(this, "No browser available", Toast.LENGTH_SHORT).show(); }
    }

    private void downloadFile(String url) {
        if (!allowed(url)) {
            Toast.makeText(this, "Unsafe download link blocked", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = Uri.parse(url);
            String name = uri.getLastPathSegment();
            if (name == null || name.isEmpty()) name = "homebrew-download";
            name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
            if (name.length() > 120) name = name.substring(name.length()-120);
            if (name.equals(".") || name.equals("..")) name = "homebrew-download";
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle(name);
            request.setDescription("Downloaded from the original game source");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            DownloadManager manager = (DownloadManager)getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                manager.enqueue(request);
                Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Could not start download: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private final class SourceBridge {
        @JavascriptInterface public void open(String url) {
            runOnUiThread(() -> openExternal(url));
        }
        @JavascriptInterface public void download(String url) {
            runOnUiThread(() -> downloadFile(url));
        }
    }

    @Override public void onBackPressed() {
        // The WebView hosts a single-page catalogue. Native back exits the app.
        super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (view != null) {
            view.removeJavascriptInterface("RetroShelfBridge");
            view.destroy();
        }
        super.onDestroy();
    }
}
