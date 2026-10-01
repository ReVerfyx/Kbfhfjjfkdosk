package com.reverfyx.mamabirdforum;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER = 4001;
    private static final String PREFS = "mama_forum";
    private static final String KEY_URL = "server_url";

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(7,8,12));
        getWindow().setNavigationBarColor(Color.rgb(7,8,12));
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildUi();

        String url = prefs.getString(KEY_URL, "");
        if (url.isEmpty()) {
            showServerDialog(true);
        } else {
            loadForum(url);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(7,8,12));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(8), dp(10), dp(8));
        bar.setBackgroundColor(Color.rgb(10,12,18));

        TextView title = new TextView(this);
        title.setText("🕊  МАМА-ПТИЦА · ФОРУМ");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setTypeface(null, 1);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));

        Button settingsButton = new Button(this);
        settingsButton.setText("⚙");
        settingsButton.setTextSize(18);
        settingsButton.setTextColor(Color.WHITE);
        settingsButton.setBackgroundColor(Color.TRANSPARENT);
        settingsButton.setOnClickListener(v -> showServerDialog(false));
        bar.addView(settingsButton, new LinearLayout.LayoutParams(dp(56), dp(48)));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(7,8,12));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUserAgentString(settings.getUserAgentString() + " MamaBirdForum/1.0");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
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

        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    private void showServerDialog(boolean required) {
        EditText input = new EditText(this);
        input.setHint("https://example.com");
        input.setSingleLine(true);
        input.setText(prefs.getString(KEY_URL, ""));
        input.setPadding(dp(14), dp(10), dp(14), dp(10));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Адрес сайта")
                .setMessage("Введи домен или IP сервера. Приложение откроет /forum.")
                .setView(input)
                .setCancelable(!required)
                .setPositiveButton("Сохранить", null)
                .setNegativeButton(required ? null : "Отмена", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = input.getText().toString().trim();
            if (!value.startsWith("http://") && !value.startsWith("https://")) {
                value = "https://" + value;
            }
            while (value.endsWith("/")) value = value.substring(0, value.length()-1);
            if (value.length() < 8) {
                input.setError("Укажи адрес сервера");
                return;
            }
            prefs.edit().putString(KEY_URL, value).apply();
            dialog.dismiss();
            loadForum(value);
        }));
        dialog.show();
    }

    private void loadForum(String root) {
        String target = root.endsWith("/forum") ? root : root + "/forum";
        webView.loadUrl(target);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
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

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
