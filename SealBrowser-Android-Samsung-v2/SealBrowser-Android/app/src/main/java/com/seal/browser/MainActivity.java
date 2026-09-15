package com.seal.browser;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final String HOME_URL = "file:///android_asset/home.html";
    private static final String PREFS = "seal_browser";

    private static final int BLUE = Color.rgb(10, 132, 255);
    private static final int BG = Color.rgb(0, 0, 0);
    private static final int CHROME = Color.rgb(18, 18, 20);
    private static final int SURFACE = Color.rgb(28, 28, 30);
    private static final int SURFACE_2 = Color.rgb(44, 44, 46);
    private static final int BORDER = Color.rgb(56, 56, 58);
    private static final int TEXT = Color.rgb(242, 242, 247);
    private static final int MUTED = Color.rgb(142, 142, 147);

    private final ArrayList<BrowserTab> tabs = new ArrayList<>();
    private int activeIndex = -1;
    private SharedPreferences prefs;

    private FrameLayout root;
    private LinearLayout appColumn;
    private FrameLayout webContainer;
    private EditText addressBar;
    private ImageView addressIcon;
    private ImageButton reloadButton;
    private ImageButton backButton;
    private ImageButton forwardButton;
    private TextView tabButton;
    private ProgressBar progress;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().getDecorView().setSystemUiVisibility(0);

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

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.INVISIBLE);
        progress.setProgressTintList(ColorStateList.valueOf(BLUE));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        appColumn.addView(progress, new LinearLayout.LayoutParams(-1, dp(2)));

        webContainer = new FrameLayout(this);
        webContainer.setBackgroundColor(BG);
        appColumn.addView(webContainer, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(10), dp(8), dp(10), dp(6));
        bottom.setBackgroundColor(CHROME);

        View separator = new View(this);
        separator.setBackgroundColor(Color.rgb(44, 44, 46));
        LinearLayout.LayoutParams separatorParams = new LinearLayout.LayoutParams(-1, dp(1));
        separatorParams.setMargins(-dp(10), -dp(8), -dp(10), dp(7));
        bottom.addView(separator, separatorParams);

        LinearLayout addressShell = new LinearLayout(this);
        addressShell.setOrientation(LinearLayout.HORIZONTAL);
        addressShell.setGravity(Gravity.CENTER_VERTICAL);
        addressShell.setPadding(dp(10), 0, dp(6), 0);
        addressShell.setBackground(rounded(SURFACE, Color.TRANSPARENT, 11, 0));

        addressIcon = new ImageView(this);
        addressIcon.setImageResource(R.drawable.ic_search);
        addressIcon.setImageTintList(ColorStateList.valueOf(MUTED));
        addressIcon.setScaleType(ImageView.ScaleType.CENTER);
        addressShell.addView(addressIcon, new LinearLayout.LayoutParams(dp(24), dp(42)));

        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setTextColor(TEXT);
        addressBar.setHintTextColor(MUTED);
        addressBar.setHint("Search or enter website");
        addressBar.setTextSize(15);
        addressBar.setGravity(Gravity.CENTER_VERTICAL);
        addressBar.setPadding(dp(4), 0, dp(4), 0);
        addressBar.setSelectAllOnFocus(false);
        addressBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressBar.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        addressBar.setBackgroundColor(Color.TRANSPARENT);
        addressBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                navigateFromBar();
                return true;
            }
            return false;
        });
        addressBar.setOnFocusChangeListener((v, focused) -> {
            if (focused) {
                addressBar.setGravity(Gravity.CENTER_VERTICAL);
                BrowserTab t = currentTab();
                String full = t == null ? "" : getDisplayFullUrl(t);
                if (!HOME_URL.equals(full)) addressBar.setText(full);
                addressBar.selectAll();
            } else {
                updateAddressPresentation();
            }
        });
        addressShell.addView(addressBar, new LinearLayout.LayoutParams(0, dp(42), 1f));

        reloadButton = iconButton(R.drawable.ic_reload, "Reload");
        reloadButton.setOnClickListener(v -> {
            BrowserTab t = currentTab();
            if (t == null) return;
            if (t.webView.getProgress() < 100) t.webView.stopLoading();
            else t.webView.reload();
        });
        addressShell.addView(reloadButton, new LinearLayout.LayoutParams(dp(38), dp(42)));

        bottom.addView(addressShell, new LinearLayout.LayoutParams(-1, dp(42)));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(0, dp(5), 0, 0);

        backButton = iconButton(R.drawable.ic_back, "Back");
        backButton.setOnClickListener(v -> {
            BrowserTab t = currentTab();
            if (t != null && t.webView.canGoBack()) t.webView.goBack();
        });
        toolbar.addView(backButton, navParams());

        forwardButton = iconButton(R.drawable.ic_forward, "Forward");
        forwardButton.setOnClickListener(v -> {
            BrowserTab t = currentTab();
            if (t != null && t.webView.canGoForward()) t.webView.goForward();
        });
        toolbar.addView(forwardButton, navParams());

        ImageButton plus = iconButton(R.drawable.ic_add, "New tab");
        plus.setOnClickListener(v -> addTab(HOME_URL, true));
        toolbar.addView(plus, navParams());

        FrameLayout tabShell = new FrameLayout(this);
        tabShell.setForegroundGravity(Gravity.CENTER);
        tabButton = new TextView(this);
        tabButton.setGravity(Gravity.CENTER);
        tabButton.setTextColor(BLUE);
        tabButton.setTextSize(10);
        tabButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tabButton.setBackground(rounded(Color.TRANSPARENT, BLUE, 4, 1));
        tabButton.setOnClickListener(v -> showTabs());
        FrameLayout.LayoutParams tabBoxParams = new FrameLayout.LayoutParams(dp(24), dp(22), Gravity.CENTER);
        tabShell.addView(tabButton, tabBoxParams);
        tabShell.setOnClickListener(v -> showTabs());
        toolbar.addView(tabShell, navParams());

        ImageButton menu = iconButton(R.drawable.ic_more, "Menu");
        menu.setOnClickListener(v -> showMenu());
        toolbar.addView(menu, navParams());

        bottom.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(45)));
        appColumn.addView(bottom, new LinearLayout.LayoutParams(-1, dp(103)));
    }

    private LinearLayout.LayoutParams navParams() {
        return new LinearLayout.LayoutParams(0, dp(40), 1f);
    }

    private ImageButton iconButton(int drawable, String description) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(drawable);
        b.setImageTintList(ColorStateList.valueOf(BLUE));
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setPadding(dp(10), dp(9), dp(10), dp(9));
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        b.setContentDescription(description);
        return b;
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
        s.setTextZoom(100);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            s.setSafeBrowsingEnabled(true);
        }

        web.setBackgroundColor(BG);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setScrollbarFadingEnabled(true);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme) || "file".equalsIgnoreCase(scheme)) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (ActivityNotFoundException ignored) {
                }
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (view.getTag() == currentTab()) {
                    progress.setVisibility(View.VISIBLE);
                    updateNavigationButtons();
                    updateAddressPresentation();
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                BrowserTab tab = (BrowserTab) view.getTag();
                if (tab != null) {
                    tab.url = url;
                    String title = view.getTitle();
                    if (title != null && !title.trim().isEmpty()) tab.title = title;
                }
                if (tab == currentTab()) {
                    progress.setVisibility(View.INVISIBLE);
                    updateNavigationButtons();
                    updateAddressPresentation();
                }
                saveTabs();
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (view.getTag() == currentTab()) {
                    progress.setVisibility(newProgress >= 100 ? View.INVISIBLE : View.VISIBLE);
                    progress.setProgress(newProgress);
                    if (reloadButton != null) {
                        reloadButton.setImageResource(newProgress >= 100 ? R.drawable.ic_reload : R.drawable.ic_close);
                    }
                }
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                BrowserTab tab = (BrowserTab) view.getTag();
                if (tab != null && title != null && !title.trim().isEmpty()) {
                    tab.title = title;
                    saveTabs();
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
        for (int i = 0; i < tabs.size(); i++) {
            tabs.get(i).webView.setVisibility(i == index ? View.VISIBLE : View.GONE);
        }
        activeIndex = index;
        BrowserTab tab = tabs.get(index);
        tab.webView.bringToFront();
        updateAddressPresentation();
        updateNavigationButtons();
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
        Dialog dialog = new Dialog(this, android.R.style.Theme_Material_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(14), dp(12), dp(14), dp(14));
        page.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("Tabs", 28, TEXT, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(54), 1f));

        TextView newTab = text("+", 28, BLUE, false);
        newTab.setGravity(Gravity.CENTER);
        newTab.setOnClickListener(v -> {
            dialog.dismiss();
            addTab(HOME_URL, true);
        });
        header.addView(newTab, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView done = text("Done", 16, BLUE, true);
        done.setGravity(Gravity.CENTER);
        done.setOnClickListener(v -> dialog.dismiss());
        header.addView(done, new LinearLayout.LayoutParams(dp(62), dp(44)));
        page.addView(header, new LinearLayout.LayoutParams(-1, dp(58)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        grid.setUseDefaultMargins(false);
        grid.setPadding(0, dp(4), 0, dp(20));
        scroll.addView(grid, new ScrollView.LayoutParams(-1, -2));

        int available = getResources().getDisplayMetrics().widthPixels - dp(38);
        int cardWidth = available / 2;

        for (int i = 0; i < tabs.size(); i++) {
            final int idx = i;
            BrowserTab tab = tabs.get(i);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(7), dp(7), dp(7), dp(8));
            card.setBackground(rounded(SURFACE, idx == activeIndex ? BLUE : BORDER, 13, idx == activeIndex ? 2 : 1));

            FrameLayout previewShell = new FrameLayout(this);
            previewShell.setBackground(rounded(SURFACE_2, Color.TRANSPARENT, 9, 0));
            previewShell.setClipToOutline(true);

            ImageView preview = new ImageView(this);
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Bitmap shot = snapshot(tab.webView);
            if (shot != null) preview.setImageBitmap(shot);
            previewShell.addView(preview, new FrameLayout.LayoutParams(-1, -1));

            TextView close = text("×", 23, TEXT, false);
            close.setGravity(Gravity.CENTER);
            close.setBackground(rounded(Color.argb(205, 44, 44, 46), Color.TRANSPARENT, 15, 0));
            close.setOnClickListener(v -> {
                dialog.dismiss();
                closeTab(idx);
            });
            FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(31), dp(31), Gravity.TOP | Gravity.RIGHT);
            closeParams.setMargins(0, dp(5), dp(5), 0);
            previewShell.addView(close, closeParams);

            card.addView(previewShell, new LinearLayout.LayoutParams(-1, dp(150)));

            TextView tabTitle = text(tab.title == null || tab.title.trim().isEmpty() ? "New Tab" : tab.title, 14, TEXT, true);
            tabTitle.setSingleLine(true);
            tabTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, dp(24));
            titleParams.setMargins(dp(3), dp(7), dp(3), 0);
            card.addView(tabTitle, titleParams);

            TextView host = text(displayUrl(tab.url), 11, MUTED, false);
            host.setSingleLine(true);
            host.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams hostParams = new LinearLayout.LayoutParams(-1, dp(20));
            hostParams.setMargins(dp(3), 0, dp(3), 0);
            card.addView(host, hostParams);

            card.setOnClickListener(v -> {
                dialog.dismiss();
                switchTo(idx);
            });

            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.width = cardWidth - dp(5);
            gp.height = dp(217);
            gp.setMargins(dp(3), dp(5), dp(3), dp(5));
            grid.addView(card, gp);
        }

        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        dialog.setContentView(page);
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setStatusBarColor(BG);
            w.setNavigationBarColor(BG);
            w.setLayout(-1, -1);
        }
    }

    private Bitmap snapshot(WebView webView) {
        try {
            int width = webView.getWidth();
            int height = webView.getHeight();
            if (width <= 0 || height <= 0) return null;
            int targetWidth = Math.min(width, dp(340));
            int targetHeight = Math.max(dp(160), (int) (targetWidth * 1.4f));
            Bitmap bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bitmap);
            float sx = targetWidth / (float) width;
            float sy = targetHeight / (float) height;
            canvas.scale(sx, sy);
            webView.draw(canvas);
            return bitmap;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void showMenu() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(8), dp(8), dp(12));
        box.setBackground(rounded(SURFACE, BORDER, 18, 1));

        TextView newTab = menuItem("New Tab");
        newTab.setOnClickListener(v -> {
            dialog.dismiss();
            addTab(HOME_URL, true);
        });
        box.addView(newTab);
        box.addView(menuDivider());

        TextView home = menuItem("Start Page");
        home.setOnClickListener(v -> {
            dialog.dismiss();
            BrowserTab t = currentTab();
            if (t != null) t.webView.loadUrl(HOME_URL);
        });
        box.addView(home);
        box.addView(menuDivider());

        TextView reload = menuItem("Reload");
        reload.setOnClickListener(v -> {
            dialog.dismiss();
            BrowserTab t = currentTab();
            if (t != null) t.webView.reload();
        });
        box.addView(reload);
        box.addView(menuDivider());

        TextView copy = menuItem("Copy Address");
        copy.setOnClickListener(v -> {
            dialog.dismiss();
            BrowserTab t = currentTab();
            if (t == null) return;
            String u = t.webView.getUrl();
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("URL", u == null ? "" : u));
            Toast.makeText(this, "Address copied", Toast.LENGTH_SHORT).show();
        });
        box.addView(copy);
        box.addView(menuDivider());

        TextView share = menuItem("Share Page");
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
            w.setDimAmount(0.35f);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.92f), -2);
            w.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.y = dp(18);
            w.setAttributes(lp);
        }
    }

    private View menuDivider() {
        View v = new View(this);
        v.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(1));
        p.setMargins(dp(12), 0, dp(12), 0);
        v.setLayoutParams(p);
        return v;
    }

    private TextView menuItem(String label) {
        TextView v = text(label, 16, TEXT, false);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(dp(14), 0, dp(14), 0);
        v.setBackgroundColor(Color.TRANSPARENT);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(50)));
        return v;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private String getDisplayFullUrl(BrowserTab tab) {
        if (tab == null) return "";
        String url = tab.webView.getUrl();
        if (url == null || url.isEmpty()) url = tab.url;
        return url == null ? "" : url;
    }

    private void updateAddressPresentation() {
        if (addressBar == null || addressBar.hasFocus()) return;
        BrowserTab t = currentTab();
        if (t == null) return;
        String url = getDisplayFullUrl(t);
        boolean home = url.isEmpty() || HOME_URL.equals(url);
        addressBar.setGravity(home ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
        addressBar.setText(home ? "" : displayUrl(url));
        addressBar.setHint("Search or enter website");
        addressIcon.setImageResource(home ? R.drawable.ic_search : R.drawable.ic_lock);
        addressIcon.setImageTintList(ColorStateList.valueOf(MUTED));
    }

    private String displayUrl(String url) {
        if (url == null || url.isEmpty() || HOME_URL.equals(url)) return "New Tab";
        try {
            Uri u = Uri.parse(url);
            String host = u.getHost();
            if (host == null) return url;
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return url;
        }
    }

    private void updateNavigationButtons() {
        BrowserTab t = currentTab();
        boolean canBack = t != null && t.webView.canGoBack();
        boolean canForward = t != null && t.webView.canGoForward();
        setButtonEnabled(backButton, canBack);
        setButtonEnabled(forwardButton, canForward);
    }

    private void setButtonEnabled(ImageButton b, boolean enabled) {
        if (b == null) return;
        b.setEnabled(enabled);
        b.setAlpha(enabled ? 1f : 0.28f);
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
            prefs.edit()
                    .putString("tabs", a.toString())
                    .putInt("active", Math.max(activeIndex, 0))
                    .apply();
        } catch (Exception ignored) {
        }
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
                tabs.get(tabs.size() - 1).title = o.optString("title", "New Tab");
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
        if (strokeDp > 0) g.setStroke(dp(strokeDp), stroke);
        return g;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void hideKeyboard() {
        View v = getCurrentFocus();
        if (v != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
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
