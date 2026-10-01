package com.reverfyx.spasaemmamuptitsu;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER = 4001;
    private static final String BASE_URL = "https://mellstroy.work.gd";

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(7, 8, 12));
        getWindow().setNavigationBarColor(Color.rgb(7, 8, 12));
        buildUi();

        if (state == null) {
            open("/");
        } else {
            webView.restoreState(state);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(7, 8, 12));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(14), dp(7), dp(8), dp(7));
        top.setBackgroundColor(Color.rgb(9, 11, 17));

        TextView title = new TextView(this);
        title.setText("СПАСАЕМ МАМУ-ПТИЦУ");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setTypeface(null, 1);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));

        Button refresh = makeTopButton("↻");
        refresh.setOnClickListener(v -> webView.reload());
        top.addView(refresh, new LinearLayout.LayoutParams(dp(52), dp(48)));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(7, 8, 12));

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        ws.setUserAgentString(ws.getUserAgentString() + " SpasaemMamuPtitsu/2.0");

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost();
                if (host != null && (host.equals("mellstroy.work.gd") || host.equals("www.mellstroy.work.gd"))) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    return true;
                } catch (Exception ignored) {
                    return false;
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params
            ) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    Intent intent = params.createIntent();
                    intent.setType("image/*");
                    startActivityForResult(intent, FILE_CHOOSER);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(dp(4), dp(5), dp(4), dp(5));
        bottom.setBackgroundColor(Color.rgb(9, 11, 17));

        Button home = makeNavButton("Главная");
        Button status = makeNavButton("Статус");
        Button forum = makeNavButton("Форум");
        Button mellai = makeNavButton("@mellai");

        home.setOnClickListener(v -> open("/"));
        status.setOnClickListener(v -> open("/status"));
        forum.setOnClickListener(v -> open("/forum"));
        mellai.setOnClickListener(v -> open("/u/mellai"));

        bottom.addView(home, navParams());
        bottom.addView(status, navParams());
        bottom.addView(forum, navParams());
        bottom.addView(mellai, navParams());

        root.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        root.addView(bottom, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(62)
        ));

        setContentView(root);
    }

    private Button makeTopButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(19);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setAllCaps(false);
        return b;
    }

    private Button makeNavButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setTextColor(Color.rgb(225, 228, 236));
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setAllCaps(false);
        b.setPadding(dp(2), 0, dp(2), 0);
        return b;
    }

    private LinearLayout.LayoutParams navParams() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
    }

    private void open(String path) {
        webView.loadUrl(BASE_URL + path);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            open("/");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER && fileCallback != null) {
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
