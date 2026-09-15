package com.seal.browser;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final String HOME_URL = "file:///android_asset/home.html";
    private static final String PREFS = "seal_browser";
    private static final int BLUE = Color.rgb(20, 115, 230);
    private static final int BG = Color.rgb(9, 17, 31);
    private static final int PANEL = Color.rgb(18, 27, 41);
    private static final int BORDER = Color.rgb(39, 51, 72);
    private static final int MUTED = Color.rgb(151, 164, 184);

    private final ArrayList<BrowserTab> tabs = new ArrayList<>();
    private int activeIndex = -1;
    private SharedPreferences prefs;

    private FrameLayout root;
    private LinearLayout appColumn;
    private FrameLayout webContainer;
    private EditText addressBar;
    private TextView tabButton;
    private ProgressBar progress;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildUi();
        restoreTabs();
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        setContentView(root);

        appColumn = new LinearLayout(this);
        appColumn.setOrientation(LinearLayout.VERTICAL);
        appColumn.setBackgroundColor(BG);
        root.addView(appColumn, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), 0, dp(12), 0);
        header.setBackgroundColor(BG);
        TextView brand = new TextView(this);
        brand.setText("Seal");
        brand.setTextColor(Color.WHITE);
        brand.setTextSize(16);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(44), 1f));

        TextView newTab = toolbarText("+");
        newTab.setContentDescription("New tab");
        newTab.setOnClickListener(v -> addTab(HOME_URL, true));
        header.addView(newTab, new LinearLayout.LayoutParams(dp(44), dp(44)));
        appColumn.addView(header, new LinearLayout.LayoutParams(-1, dp(44)));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(BLUE));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(BG));
        appColumn.addView(progress, new LinearLayout.LayoutParams(-1, dp(2)));

        webContainer = new FrameLayout(this);
        webContainer.setBackgroundColor(Color.WHITE);
        appColumn.addView(webContainer, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout lower = new LinearLayout(this);
        lower.setOrientation(LinearLayout.VERTICAL);
        lower.setPadding(dp(8), dp(6), dp(8), dp(8));
        lower.setBackgroundColor(BG);

        LinearLayout addressRow = new LinearLayout(this);
        addressRow.setGravity(Gravity.CENTER_VERTICAL);
        addressRow.setPadding(dp(4), 0, dp(4), 0);

        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setTextColor(Color.WHITE);
        addressBar.setHintTextColor(MUTED);
        addressBar.setHint("Search or enter website");
        addressBar.setTextSize(15);
        addressBar.setPadding(dp(14), 0, dp(14), 0);
        addressBar.setSelectAllOnFocus(false);
        addressBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressBar.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        addressBar.setBackground(rounded(PANEL, BORDER, 16, 1));
        addressBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                navigateFromBar();
                return true;
            }
            return false;
        });
        addressBar.setOnFocusChangeListener((v, focused) -> {
            if (focused) addressBar.selectAll();
        });
        addressRow.addView(addressBar, new LinearLayout.LayoutParams(0, dp(48), 1f));

        TextView reload = compactButton("↻");
        reload.setContentDescription("Reload");
        reload.setOnClickListener(v -> {
            BrowserTab tab = currentTab();
            if (tab != null) tab.webView.reload();
        });
        LinearLayout.LayoutParams smallButtonParams = new LinearLayout.LayoutParams(dp(46), dp(48));
        smallButtonParams.setMargins(dp(6), 0, 0, 0);
        addressRow.addView(reload, smallButtonParams);
        lower.addView(addressRow, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(2), dp(4), dp(2), 0);

        TextView back = navButton("‹");
        back.setOnClickListener(v -> { BrowserTab t=currentTab(); if(t!=null && t.webView.canGoBack()) t.webView.goBack(); });
        nav.addView(back, navParams());

        TextView forward = navButton("›");
        forward.setOnClickListener(v -> { BrowserTab t=currentTab(); if(t!=null && t.webView.canGoForward()) t.webView.goForward(); });
        nav.addView(forward, navParams());

        TextView home = navButton("⌂");
        home.setOnClickListener(v -> { BrowserTab t=currentTab(); if(t!=null) t.webView.loadUrl(HOME_URL); });
        nav.addView(home, navParams());

        tabButton = navButton("1");
        tabButton.setTextSize(14);
        tabButton.setBackground(rounded(PANEL, BORDER, 10, 1));
        tabButton.setOnClickListener(v -> showTabs());
        nav.addView(tabButton, navParams());

        TextView menu = navButton("•••");
        menu.setTextSize(17);
        menu.setOnClickListener(v -> showMenu());
        nav.addView(menu, navParams());

        lower.addView(nav, new LinearLayout.LayoutParams(-1, dp(50)));
        appColumn.addView(lower, new LinearLayout.LayoutParams(-1, dp(112)));
    }

    private LinearLayout.LayoutParams navParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(46), 1f);
        p.setMargins(dp(3), 0, dp(3), 0);
        return p;
    }

    private TextView toolbarText(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(Color.WHITE);
        v.setTextSize(28);
        v.setGravity(Gravity.CENTER);
        return v;
    }

    private TextView compactButton(String text) {
        TextView v = toolbarText(text);
        v.setTextSize(24);
        v.setBackground(rounded(PANEL, BORDER, 15, 1));
        return v;
    }

    private TextView navButton(String text) {
        TextView v = toolbarText(text);
        v.setTextSize(28);
        v.setBackgroundColor(Color.TRANSPARENT);
        return v;
    }

    private void navigateFromBar() {
        BrowserTab tab = currentTab();
        if (tab == null) return;
        String raw = addressBar.getText().toString().trim();
        if (raw.isEmpty()) return;
        String url;
        if (raw.matches("(?i)^https?://.*")) {
            url = raw;
        } else if (raw.matches("(?i)^[a-z][a-z0-9+.-]*:.*")) {
            url = raw;
        } else if (raw.contains(".") && !raw.contains(" ")) {
            url = "https://" + raw;
        } else {
            try {
                url = "https://www.google.com/search?hl=en&gl=gb&q=" + URLEncoder.encode(raw, "UTF-8");
            } catch (Exception e) {
                url = "https://www.google.com/search?hl=en&gl=gb&q=" + raw.replace(" ", "+");
            }
        }
        tab.webView.loadUrl(url);
        hideKeyboard();
        addressBar.clearFocus();
    }

    private void addTab(String url, boolean activate) {
        WebView web = new WebView(this);
        configureWebView(web);
        BrowserTab tab = new BrowserTab(web, url);
        web.setTag(tab);
        tabs.add(tab);
        webContainer.addView(web, new FrameLayout.LayoutParams(-1, -1));
        if (activate) switchTo(tabs.size() - 1);
        else web.setVisibility(View.GONE);
        web.loadUrl(url == null || url.isEmpty() ? HOME_URL : url);
        updateTabCount();
        saveTabs();
    }

    private void configureWebView(WebView web) {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setAllowContentAccess(true);
        s.setAllowFileAccess(true);
        if (android.os.Build.VERSION.SDK_INT >= 21) s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme) || "file".equalsIgnoreCase(scheme)) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (ActivityNotFoundException ignored) {}
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                BrowserTab tab = (BrowserTab) view.getTag();
                if (tab != null) {
                    tab.url = url;
                    String title = view.getTitle();
                    if (title != null && !title.trim().isEmpty()) tab.title = title;
                }
                if (tab == currentTab()) updateAddress(url);
                saveTabs();
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (view.getTag() == currentTab()) {
                    progress.setVisibility(newProgress >= 100 ? View.INVISIBLE : View.VISIBLE);
                    progress.setProgress(newProgress);
                }
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                WebView child = new WebView(MainActivity.this);
                configureWebView(child);
                BrowserTab tab = new BrowserTab(child, HOME_URL);
                child.setTag(tab);
                tabs.add(tab);
                webContainer.addView(child, new FrameLayout.LayoutParams(-1, -1));
                switchTo(tabs.size() - 1);
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(child);
                resultMsg.sendToTarget();
                updateTabCount();
                saveTabs();
                return true;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                appColumn.setVisibility(View.GONE);
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
            }

            @Override
            public void onHideCustomView() {
                hideCustomView();
            }
        });

        web.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Couldn't open download", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void hideCustomView() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        appColumn.setVisibility(View.VISIBLE);
        if (customViewCallback != null) customViewCallback.onCustomViewHidden();
        customViewCallback = null;
    }

    private void switchTo(int index) {
        if (index < 0 || index >= tabs.size()) return;
        for (int i = 0; i < tabs.size(); i++) tabs.get(i).webView.setVisibility(i == index ? View.VISIBLE : View.GONE);
        activeIndex = index;
        BrowserTab tab = tabs.get(index);
        tab.webView.bringToFront();
        updateAddress(tab.webView.getUrl() != null ? tab.webView.getUrl() : tab.url);
        updateTabCount();
        saveTabs();
    }

    private BrowserTab currentTab() {
        return activeIndex >= 0 && activeIndex < tabs.size() ? tabs.get(activeIndex) : null;
    }

    private void closeTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        BrowserTab removed = tabs.remove(index);
        webContainer.removeView(removed.webView);
        removed.webView.destroy();
        if (tabs.isEmpty()) {
            activeIndex = -1;
            addTab(HOME_URL, true);
            return;
        }
        if (activeIndex >= tabs.size()) activeIndex = tabs.size() - 1;
        else if (index < activeIndex) activeIndex--;
        switchTo(Math.max(0, activeIndex));
        saveTabs();
    }

    private void showTabs() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(16), dp(18), dp(18));
        page.setBackgroundColor(BG);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Tabs", 24, Color.WHITE, true);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(50), 1f));
        TextView plus = compactButton("+");
        plus.setOnClickListener(v -> { dialog.dismiss(); addTab(HOME_URL, true); });
        top.addView(plus, new LinearLayout.LayoutParams(dp(46), dp(46)));
        page.addView(top, new LinearLayout.LayoutParams(-1, dp(54)));

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new android.widget.ScrollView.LayoutParams(-1, -2));

        for (int i = 0; i < tabs.size(); i++) {
            final int idx = i;
            BrowserTab tab = tabs.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(14), dp(10), dp(8), dp(10));
            int fill = idx == activeIndex ? Color.rgb(18, 48, 82) : PANEL;
            card.setBackground(rounded(fill, idx == activeIndex ? BLUE : BORDER, 16, 1));

            LinearLayout copy = new LinearLayout(this);
            copy.setOrientation(LinearLayout.VERTICAL);
            TextView t = text(tab.title == null ? "New Tab" : tab.title, 16, Color.WHITE, false);
            t.setSingleLine(true);
            TextView u = text(displayUrl(tab.url), 12, MUTED, false);
            u.setSingleLine(true);
            copy.addView(t, new LinearLayout.LayoutParams(-1, dp(25)));
            copy.addView(u, new LinearLayout.LayoutParams(-1, dp(21)));
            card.addView(copy, new LinearLayout.LayoutParams(0, dp(50), 1f));

            TextView close = navButton("×");
            close.setTextSize(25);
            close.setOnClickListener(v -> { dialog.dismiss(); closeTab(idx); });
            card.addView(close, new LinearLayout.LayoutParams(dp(44), dp(48)));
            card.setOnClickListener(v -> { dialog.dismiss(); switchTo(idx); });

            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(72));
            cp.setMargins(0, dp(6), 0, dp(6));
            list.addView(card, cp);
        }

        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        dialog.setContentView(page);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setLayout(-1, -1);
            w.setDimAmount(0.6f);
        }
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setLayout(-1, -1);
    }

    private void showMenu() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(16));
        box.setBackground(rounded(PANEL, BORDER, 20, 1));

        TextView newTab = menuItem("New tab");
        newTab.setOnClickListener(v -> { dialog.dismiss(); addTab(HOME_URL, true); });
        box.addView(newTab);
        TextView home = menuItem("Home");
        home.setOnClickListener(v -> { dialog.dismiss(); BrowserTab t=currentTab(); if(t!=null)t.webView.loadUrl(HOME_URL); });
        box.addView(home);
        TextView copy = menuItem("Copy page address");
        copy.setOnClickListener(v -> {
            dialog.dismiss();
            BrowserTab t = currentTab();
            if (t == null) return;
            String u = t.webView.getUrl();
            android.content.ClipboardManager cm = (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("URL", u == null ? "" : u));
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
        });
        box.addView(copy);
        TextView share = menuItem("Share page");
        share.setOnClickListener(v -> {
            dialog.dismiss();
            BrowserTab t = currentTab();
            if (t == null) return;
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, t.webView.getUrl());
            startActivity(Intent.createChooser(i, "Share"));
        });
        box.addView(share);

        dialog.setContentView(box);
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setLayout((int)(getResources().getDisplayMetrics().widthPixels * 0.88f), -2);
            w.setGravity(Gravity.BOTTOM);
        }
    }

    private TextView menuItem(String label) {
        TextView v = text(label, 16, Color.WHITE, false);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(dp(12), 0, dp(12), 0);
        v.setBackgroundColor(Color.TRANSPARENT);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(52)));
        return v;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return v;
    }

    private void updateAddress(String url) {
        if (addressBar == null || addressBar.hasFocus()) return;
        if (url == null || HOME_URL.equals(url)) addressBar.setText("");
        else addressBar.setText(url);
    }

    private String displayUrl(String url) {
        if (url == null || HOME_URL.equals(url)) return "New Tab";
        try {
            Uri u = Uri.parse(url);
            return u.getHost() == null ? url : u.getHost();
        } catch (Exception e) { return url; }
    }

    private void updateTabCount() {
        if (tabButton != null) tabButton.setText(String.valueOf(Math.max(1, tabs.size())));
    }

    private void saveTabs() {
        try {
            JSONArray a = new JSONArray();
            for (BrowserTab t : tabs) {
                JSONObject o = new JSONObject();
                String url = t.webView.getUrl();
                if (url == null || url.isEmpty()) url = t.url;
                o.put("url", url == null ? HOME_URL : url);
                o.put("title", t.webView.getTitle() == null ? t.title : t.webView.getTitle());
                a.put(o);
            }
            prefs.edit().putString("tabs", a.toString()).putInt("active", Math.max(activeIndex, 0)).apply();
        } catch (Exception ignored) {}
    }

    private void restoreTabs() {
        String saved = prefs.getString("tabs", null);
        if (saved == null) {
            addTab(HOME_URL, true);
            return;
        }
        try {
            JSONArray a = new JSONArray(saved);
            for (int i = 0; i < a.length() && i < 30; i++) {
                JSONObject o = a.getJSONObject(i);
                addTab(o.optString("url", HOME_URL), false);
                tabs.get(tabs.size()-1).title = o.optString("title", "New Tab");
            }
            if (tabs.isEmpty()) addTab(HOME_URL, true);
            else switchTo(Math.min(prefs.getInt("active", 0), tabs.size() - 1));
        } catch (Exception e) {
            addTab(HOME_URL, true);
        }
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(dp(strokeDp), stroke);
        return g;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void hideKeyboard() {
        View v = getCurrentFocus();
        if (v != null) {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
    }

    @Override
    protected void onPause() {
        saveTabs();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        saveTabs();
        for (BrowserTab t : tabs) t.webView.destroy();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            hideCustomView();
            return;
        }
        BrowserTab t = currentTab();
        if (t != null && t.webView.canGoBack()) t.webView.goBack();
        else super.onBackPressed();
    }

    private static class BrowserTab {
        final WebView webView;
        String url;
        String title = "New Tab";
        BrowserTab(WebView webView, String url) {
            this.webView = webView;
            this.url = url;
        }
    }
}
