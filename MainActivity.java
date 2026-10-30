package com.abdulrahmandhafir.smartphones;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import androidx.webkit.WebViewAssetLoader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import org.json.JSONArray;

public class MainActivity extends Activity {
    private static final int MAX_SHARED_IMAGES = 30;
    private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
    private WebView webView;
    private WebViewAssetLoader assetLoader;
    private ArrayList<String> pendingSharedBase64Images = new ArrayList<>();
    private boolean pageLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);
        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        // The app only needs bundled assets and content URIs supplied by the share intent.
        // Disabling file-URL cross access prevents a compromised page from reading local files.
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageLoaded = true;
                flushPendingImagesToWebApp();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Keep navigation inside the local app; external links are opened by the web app
                // through explicit Android/browser intents.
                if (request == null || request.getUrl() == null) return false;
                boolean localApp = "appassets.androidplatform.net".equals(request.getUrl().getHost());
                if (localApp) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, request.getUrl()));
                } catch (Exception ignored) { }
                return true;
            }
        });

        handleGalleryShareIntent(getIntent());
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleGalleryShareIntent(intent);
        if (pageLoaded) {
            flushPendingImagesToWebApp();
        }
    }

    private void handleGalleryShareIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String type = intent.getType();

        if (type != null && type.startsWith("image/")) {
            if (Intent.ACTION_SEND.equals(action)) {
                Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (imageUri != null) {
                    String b64 = uriToDataUrl(imageUri, type);
                    addPendingImage(b64);
                }
            } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
                ArrayList<Uri> imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if (imageUris != null) {
                    for (Uri uri : imageUris) {
                        if (pendingSharedBase64Images.size() >= MAX_SHARED_IMAGES) break;
                        addPendingImage(uriToDataUrl(uri, type));
                    }
                }
            }
        }
    }

    private void addPendingImage(String dataUrl) {
        if (dataUrl != null && pendingSharedBase64Images.size() < MAX_SHARED_IMAGES) {
            pendingSharedBase64Images.add(dataUrl);
        }
    }

    private String uriToDataUrl(Uri uri, String mimeType) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) return null;
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[16384];
            int total = 0;
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                total += nRead;
                if (total > MAX_IMAGE_BYTES) {
                    is.close();
                    return null;
                }
                buffer.write(data, 0, nRead);
            }
            buffer.flush();
            is.close();
            String base64 = Base64.encodeToString(buffer.toByteArray(), Base64.NO_WRAP);
            return "data:" + (mimeType != null ? mimeType : "image/jpeg") + ";base64," + base64;
        } catch (Exception e) {
            return null;
        }
    }

    private void flushPendingImagesToWebApp() {
        if (pendingSharedBase64Images.isEmpty()) return;
        JSONArray jsonArray = new JSONArray(pendingSharedBase64Images);
        pendingSharedBase64Images.clear();
        final String jsCode = "if (window.receiveSharedGalleryImages) { window.receiveSharedGalleryImages(" + jsonArray.toString() + "); }";
        webView.post(() -> webView.evaluateJavascript(jsCode, null));
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
