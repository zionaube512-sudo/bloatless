package com.zionaube.bloatless.browser;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;

public class ZionAubeBrowserMainActivity extends Activity {
    private static final String DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private static final String HOME_URL = "https://duckduckgo.com";
    private static final String TOP_BANNER_TEXT = "Made by Zion Aube";
    private static final int FILE_CHOOSER_REQUEST = 4091;

    private EditText urlInput;
    private Button desktopBtn;
    private LinearLayout tabBar;
    private FrameLayout webContainer;
    private ValueCallback<Uri[]> filePathCallback;

    private final ArrayList<WebView> tabs = new ArrayList<>();
    private final ArrayList<Boolean> desktopModes = new ArrayList<>();
    private int currentTab = -1;
    private String defaultMobileUA;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Zion Aube Browser Window");
        try { startService(new Intent(this, ZionAubeTabBackgroundService.class)); } catch (Exception ignored) {}

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#111317"));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets sys = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
                return insets;
            });
        }

        TextView bannerView = new TextView(this);
        bannerView.setText(TOP_BANNER_TEXT);
        bannerView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        bannerView.setTextColor(Color.parseColor("#9AA5B4"));
        bannerView.setBackgroundColor(Color.parseColor("#15181E"));
        bannerView.setTextSize(11.5f);
        bannerView.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
        bannerView.setPadding(16, 7, 16, 7);
        root.addView(bannerView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(12, 8, 12, 8);
        topBar.setBackgroundColor(Color.parseColor("#1B1F27"));

        urlInput = new EditText(this);
        urlInput.setHint("Search or enter URL");
        urlInput.setHintTextColor(Color.parseColor("#7E8794"));
        urlInput.setTextColor(Color.parseColor("#F3F6FA"));
        urlInput.setBackground(makeRoundedBg(Color.parseColor("#282E3A"), Color.parseColor("#3B4352"), 18f, 1));
        urlInput.setPadding(24, 14, 20, 14);
        urlInput.setSingleLine(true);
        urlInput.setTextSize(14f);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlInput.setSelectAllOnFocus(true);
        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                loadFromBar();
                return true;
            }
            return false;
        });

        LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        urlParams.setMargins(0, 0, 8, 0);
        topBar.addView(urlInput, urlParams);

        Button goBtn = makeActionButton("Go", Color.parseColor("#2B3240"), Color.parseColor("#3E4759"), 8);
        goBtn.setOnClickListener(v -> loadFromBar());
        topBar.addView(goBtn);

        desktopBtn = makeActionButton("PC", Color.parseColor("#2B3240"), Color.parseColor("#3E4759"), 8);
        desktopBtn.setOnClickListener(v -> toggleDesktopMode());
        topBar.addView(desktopBtn);

        Button newTabBtn = makeActionButton("+", Color.parseColor("#2B3240"), Color.parseColor("#3E4759"), 0);
        newTabBtn.setOnClickListener(v -> addNewTab(HOME_URL));
        topBar.addView(newTabBtn);

        root.addView(topBar);

        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setBackgroundColor(Color.parseColor("#15181E"));
        tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setPadding(12, 6, 12, 8);
        tabScroll.addView(tabBar);
        root.addView(tabScroll);

        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#2A303C"));
        root.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2));

        webContainer = new FrameLayout(this);
        root.addView(webContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        setContentView(root);

        String initialUrl = HOME_URL;
        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            initialUrl = intent.getData().toString();
        }
        addNewTab(initialUrl);
    }

    private GradientDrawable makeRoundedBg(int fillColor, int strokeColor, float radiusPx, int strokeWidthPx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(fillColor);
        gd.setCornerRadius(radiusPx);
        if (strokeWidthPx > 0) gd.setStroke(strokeWidthPx, strokeColor);
        return gd;
    }

    private Button makeActionButton(String label, int fillColor, int strokeColor, int rightMargin) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.parseColor("#F3F6FA"));
        b.setTextSize(13f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(makeRoundedBg(fillColor, strokeColor, 16f, 1));
        b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(0); b.setMinimumHeight(0);
        b.setPadding(20, 12, 20, 12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, rightMargin, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void addNewTab(String url) {
        WebView wv;
        try {
            wv = new WebView(this);
        } catch (Throwable err) {
            TextView fallback = new TextView(this);
            fallback.setText("System WebView not available on this device.");
            fallback.setTextColor(Color.WHITE);
            fallback.setPadding(24, 24, 24, 24);
            webContainer.removeAllViews();
            webContainer.addView(fallback);
            return;
        }

        WebSettings ws = wv.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setSupportZoom(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        if (defaultMobileUA == null) defaultMobileUA = ws.getUserAgentString();

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String loadedUrl) {
                if (tabs.indexOf(view) == currentTab && !urlInput.hasFocus()) urlInput.setText(loadedUrl);
                refreshTabStrip();
            }
        });

        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView view, String title) { refreshTabStrip(); }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                Intent chooserIntent = new Intent(Intent.ACTION_GET_CONTENT);
                chooserIntent.addCategory(Intent.CATEGORY_OPENABLE);
                String[] types = params.getAcceptTypes();
                String mime = (types != null && types.length > 0 && types[0] != null && !types[0].isEmpty()) ? types[0] : "*/*";
                chooserIntent.setType(mime);
                if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) chooserIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try {
                    startActivityForResult(Intent.createChooser(chooserIntent, "Select photo or file"), FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception e) {
                    filePathCallback.onReceiveValue(null);
                    filePathCallback = null;
                    Toast.makeText(ZionAubeBrowserMainActivity.this, "No file picker available", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });

        wv.setDownloadListener((downloadUrl, userAgent, contentDisposition, mimetype, contentLength) -> {
            try {
                String fileName = URLUtil.guessFileName(downloadUrl, contentDisposition, mimetype);
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(downloadUrl));
                if (userAgent != null && !userAgent.isEmpty()) req.addRequestHeader("User-Agent", userAgent);
                String cookies = CookieManager.getInstance().getCookie(downloadUrl);
                if (cookies != null && !cookies.isEmpty()) req.addRequestHeader("Cookie", cookies);
                if (mimetype != null && !mimetype.isEmpty()) req.setMimeType(mimetype);
                req.setTitle(fileName);
                req.setDescription("Downloading via Bloatless");
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                if (dm != null) {
                    dm.enqueue(req);
                    Toast.makeText(this, "Downloading " + fileName, Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Download failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        tabs.add(wv);
        desktopModes.add(false);
        switchToTab(tabs.size() - 1);
        wv.loadUrl(url);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST || filePathCallback == null) return;
        Uri[] results = null;
        if (resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                results = new Uri[count];
                for (int i = 0; i < count; i++) results[i] = data.getClipData().getItemAt(i).getUri();
            } else if (data.getData() != null) {
                results = new Uri[] { data.getData() };
            }
        }
        filePathCallback.onReceiveValue(results);
        filePathCallback = null;
    }

    private void switchToTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        currentTab = index;
        webContainer.removeAllViews();
        WebView active = tabs.get(currentTab);
        webContainer.addView(active, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        String currentUrl = active.getUrl();
        urlInput.setText(currentUrl != null ? currentUrl : "");
        updateDesktopVisual();
        refreshTabStrip();
    }

    private void closeTab(int index) {
        if (tabs.size() <= 1) { tabs.get(0).loadUrl(HOME_URL); return; }
        WebView closing = tabs.remove(index);
        desktopModes.remove(index);
        closing.destroy();
        if (currentTab >= tabs.size()) currentTab = tabs.size() - 1;
        switchToTab(currentTab);
    }

    private void refreshTabStrip() {
        tabBar.removeAllViews();
        for (int i = 0; i < tabs.size(); i++) {
            final int idx = i;
            WebView wv = tabs.get(i);
            String title = wv.getTitle();
            if (title == null || title.trim().isEmpty()) title = "Tab " + (i + 1);
            if (title.length() > 16) title = title.substring(0, 16) + "…";
            boolean active = (i == currentTab);
            TextView tabChip = new TextView(this);
            tabChip.setText(title + "  ✕");
            tabChip.setTextSize(12.5f);
            tabChip.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
            tabChip.setTextColor(active ? Color.parseColor("#0F172A") : Color.parseColor("#CBD5E1"));
            tabChip.setBackground(makeRoundedBg(active ? Color.parseColor("#7DD3FC") : Color.parseColor("#232833"), active ? Color.parseColor("#38BDF8") : Color.parseColor("#343C4A"), 14f, 1));
            tabChip.setPadding(20, 9, 20, 9);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 10, 0);
            tabChip.setLayoutParams(lp);
            tabChip.setOnClickListener(v -> switchToTab(idx));
            tabChip.setOnLongClickListener(v -> { closeTab(idx); return true; });
            tabBar.addView(tabChip);
        }
    }

    private void loadFromBar() {
        if (currentTab < 0) return;
        String raw = urlInput.getText().toString().trim();
        if (raw.isEmpty()) return;
        String target = (raw.startsWith("http://") || raw.startsWith("https://")) ? raw : (!raw.contains(" ") && raw.contains(".")) ? "https://" + raw : "https://duckduckgo.com/?q=" + Uri.encode(raw);
        urlInput.clearFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(urlInput.getWindowToken(), 0);
        tabs.get(currentTab).loadUrl(target);
    }

    private void toggleDesktopMode() {
        if (currentTab < 0) return;
        boolean nextState = !desktopModes.get(currentTab);
        desktopModes.set(currentTab, nextState);
        WebView wv = tabs.get(currentTab);
        WebSettings ws = wv.getSettings();
        ws.setUserAgentString(nextState ? DESKTOP_UA : defaultMobileUA);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        updateDesktopVisual();
        wv.reload();
    }

    private void updateDesktopVisual() {
        boolean isDesktop = currentTab >= 0 && desktopModes.get(currentTab);
        desktopBtn.setBackground(makeRoundedBg(isDesktop ? Color.parseColor("#1E6F50") : Color.parseColor("#2B3240"), isDesktop ? Color.parseColor("#34D399") : Color.parseColor("#3E4759"), 16f, 1));
    }

    @Override
    public void onBackPressed() {
        if (currentTab >= 0 && tabs.get(currentTab).canGoBack()) tabs.get(currentTab).goBack();
        else if (tabs.size() > 1) closeTab(currentTab);
        else super.onBackPressed();
    }
}
