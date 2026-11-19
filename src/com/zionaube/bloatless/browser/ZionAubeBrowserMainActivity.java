package com.zionaube.bloatless.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class ZionAubeBrowserMainActivity extends Activity {

    private static final int FILE_CHOOSER_REQUEST_CODE = 1001;
    private static final String DEFAULT_HOME = "https://www.google.com";
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private static class TabEntry {
        WebView webView;
        String url;
        String title;
        boolean desktopMode;

        TabEntry(WebView webView, String url, boolean desktopMode) {
            this.webView = webView;
            this.url = url;
            this.title = url;
            this.desktopMode = desktopMode;
        }
    }

    private static class SdrWebView extends WebView {
        public SdrWebView(Context context) {
            super(context);
        }

        @Override
        public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
            InputConnection ic = super.onCreateInputConnection(outAttrs);
            if (outAttrs != null) {
                int action = outAttrs.imeOptions & EditorInfo.IME_MASK_ACTION;
                if (action == EditorInfo.IME_ACTION_NEXT || action == EditorInfo.IME_ACTION_NONE) {
                    outAttrs.imeOptions = (outAttrs.imeOptions & ~EditorInfo.IME_MASK_ACTION)
                            | EditorInfo.IME_ACTION_GO;
                }
                outAttrs.imeOptions &= ~EditorInfo.IME_FLAG_NO_ENTER_ACTION;
            }
            return ic;
        }
    }

    private final List<TabEntry> tabs = new ArrayList<>();
    private int currentTab = -1;

    private EditText urlInput;
    private Button menuButton;
    private FrameLayout webContainer;

    private ValueCallback<Uri[]> filePathCallback;
    private OnBackInvokedCallback backCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#121212"));
        root.setFitsSystemWindows(true);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(Color.parseColor("#1E1E1E"));
        int pad = dp(8);
        topBar.setPadding(pad, pad, pad, pad);

        urlInput = new EditText(this);
        urlInput.setHint("Search Google or enter URL");
        urlInput.setHintTextColor(Color.parseColor("#9E9E9E"));
        urlInput.setTextColor(Color.WHITE);
        urlInput.setBackgroundColor(Color.parseColor("#2C2C2C"));
        urlInput.setSingleLine(true);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlInput.setContentDescription("Address and Google search bar");
        int inputPadH = dp(12);
        int inputPadV = dp(10);
        urlInput.setPadding(inputPadH, inputPadV, inputPadH, inputPadV);
        urlInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        urlParams.setMarginEnd(dp(8));
        topBar.addView(urlInput, urlParams);

        menuButton = new Button(this);
        menuButton.setText("Menu");
        menuButton.setContentDescription("Browser Menu");
        menuButton.setTextColor(Color.WHITE);
        menuButton.setBackgroundColor(Color.parseColor("#333333"));
        menuButton.setPadding(dp(14), dp(10), dp(14), dp(10));
        menuButton.setMinimumWidth(dp(64));
        menuButton.setMinimumHeight(dp(48));
        menuButton.setOnClickListener(v -> showMainMenu());
        topBar.addView(menuButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        webContainer = new FrameLayout(this);
        root.addView(webContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO
                    || actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN)) {
                navigateFromInput();
                return true;
            }
            return false;
        });

        registerBackHandler();

        if (savedInstanceState != null && savedInstanceState.containsKey("tab_urls")) {
            ArrayList<String> savedUrls = savedInstanceState.getStringArrayList("tab_urls");
            boolean[] savedDesktop = savedInstanceState.getBooleanArray("tab_desktop");
            int savedIndex = savedInstanceState.getInt("current_tab", 0);
            if (savedUrls != null && !savedUrls.isEmpty()) {
                for (int i = 0; i < savedUrls.size(); i++) {
                    boolean desk = savedDesktop != null && i < savedDesktop.length && savedDesktop[i];
                    addNewTab(savedUrls.get(i), desk, false);
                }
                switchToTab(Math.max(0, Math.min(savedIndex, tabs.size() - 1)));
            } else {
                addNewTab(resolveIncomingUrl(getIntent()), false, true);
            }
        } else {
            addNewTab(resolveIncomingUrl(getIntent()), false, true);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String incoming = resolveIncomingUrl(intent);
        if (incoming != null && !incoming.equals(DEFAULT_HOME)) {
            addNewTab(incoming, false, true);
        }
    }

    private String resolveIncomingUrl(Intent intent) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getDataString() != null) {
            return intent.getDataString();
        }
        return DEFAULT_HOME;
    }

    private void registerBackHandler() {
        if (Build.VERSION.SDK_INT >= 33) {
            backCallback = this::handleBackNavigation;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        handleBackNavigation();
    }

    private void handleBackNavigation() {
        if (currentTab >= 0 && currentTab < tabs.size()) {
            WebView wv = tabs.get(currentTab).webView;
            if (wv != null && wv.canGoBack()) {
                wv.goBack();
                return;
            }
        }
        if (tabs.size() > 1) {
            closeTab(currentTab);
            return;
        }
        finish();
    }

    private void navigateFromInput() {
        String raw = urlInput.getText().toString().trim();
        if (raw.isEmpty()) return;
        hideKeyboard();

        String target;
        if (raw.startsWith("http://") || raw.startsWith("https://") || raw.startsWith("chrome://")) {
            target = raw;
        } else if (!raw.contains(" ") && raw.contains(".")) {
            target = "https://" + raw;
        } else {
            try {
                target = "https://www.google.com/search?q=" + URLEncoder.encode(raw, "UTF-8");
            } catch (Exception e) {
                target = "https://www.google.com/search?q=" + raw.replace(" ", "+");
            }
        }

        if (currentTab >= 0 && currentTab < tabs.size()) {
            TabEntry tab = tabs.get(currentTab);
            tab.url = target;
            tab.webView.loadUrl(target);
        } else {
            addNewTab(target, false, true);
        }
    }

    private void showMainMenu() {
        if (tabs.isEmpty()) return;
        TabEntry active = tabs.get(currentTab);
        String desktopLabel = active.desktopMode ? "Desktop Mode: On (Switch to Mobile)" : "Desktop Mode: Off (Switch to Desktop)";
        String[] items = new String[]{
                "New Tab",
                "Tabs (" + tabs.size() + ")",
                "Refresh",
                "Share",
                desktopLabel
        };

        new AlertDialog.Builder(this)
                .setTitle("Bloatless Menu")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            addNewTab(DEFAULT_HOME, false, true);
                            break;
                        case 1:
                            showTabSwitcher();
                            break;
                        case 2:
                            if (active.webView != null) active.webView.reload();
                            break;
                        case 3:
                            shareCurrentUrl();
                            break;
                        case 4:
                            toggleDesktopMode();
                            break;
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showTabSwitcher() {
        if (tabs.isEmpty()) return;
        String[] labels = new String[tabs.size()];
        for (int i = 0; i < tabs.size(); i++) {
            TabEntry t = tabs.get(i);
            String prefix = (i == currentTab) ? "[Active] " : "";
            String name = (t.title != null && !t.title.trim().isEmpty()) ? t.title : t.url;
            labels[i] = prefix + "Tab " + (i + 1) + ": " + name;
        }

        new AlertDialog.Builder(this)
                .setTitle("Open Tabs (" + tabs.size() + ")")
                .setItems(labels, (dialog, which) -> switchToTab(which))
                .setPositiveButton("New Tab", (d, w) -> addNewTab(DEFAULT_HOME, false, true))
                .setNeutralButton("Close Current Tab", (d, w) -> closeTab(currentTab))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void shareCurrentUrl() {
        if (currentTab < 0 || currentTab >= tabs.size()) return;
        TabEntry active = tabs.get(currentTab);
        String shareUrl = (active.webView != null && active.webView.getUrl() != null)
                ? active.webView.getUrl()
                : active.url;
        if (shareUrl == null || shareUrl.isEmpty()) return;

        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_TEXT, shareUrl);
        startActivity(Intent.createChooser(sendIntent, "Share link"));
    }

    private void toggleDesktopMode() {
        if (currentTab < 0 || currentTab >= tabs.size()) return;
        TabEntry active = tabs.get(currentTab);
        active.desktopMode = !active.desktopMode;
        applyDesktopSettings(active.webView.getSettings(), active.desktopMode);
        active.webView.reload();
        Toast.makeText(this, active.desktopMode ? "Desktop Mode On" : "Mobile Mode On", Toast.LENGTH_SHORT).show();
    }

    private void applyDesktopSettings(WebSettings ws, boolean desktop) {
        ws.setUserAgentString(desktop ? DESKTOP_UA : null);
        ws.setUseWideViewPort(desktop);
        ws.setLoadWithOverviewMode(desktop);
    }

    private void addNewTab(String url, boolean desktopMode, boolean switchNow) {
        WebView wv = createConfiguredWebView(desktopMode);
        TabEntry entry = new TabEntry(wv, url, desktopMode);
        tabs.add(entry);
        int newIndex = tabs.size() - 1;
        if (switchNow) {
            switchToTab(newIndex);
        }
        wv.loadUrl(url);
    }

    private void switchToTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        currentTab = index;
        webContainer.removeAllViews();
        TabEntry active = tabs.get(currentTab);
        if (active.webView.getParent() instanceof ViewGroup) {
            ((ViewGroup) active.webView.getParent()).removeView(active.webView);
        }
        webContainer.addView(active.webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        urlInput.setText(active.url != null ? active.url : "");
        menuButton.setContentDescription("Browser Menu, " + tabs.size() + " open tabs");
    }

    private void closeTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        TabEntry removed = tabs.remove(index);
        if (removed.webView != null) {
            if (removed.webView.getParent() instanceof ViewGroup) {
                ((ViewGroup) removed.webView.getParent()).removeView(removed.webView);
            }
            removed.webView.stopLoading();
            removed.webView.destroy();
        }

        if (tabs.isEmpty()) {
            currentTab = -1;
            addNewTab(DEFAULT_HOME, false, true);
            return;
        }

        if (index < currentTab) {
            currentTab--;
        } else if (currentTab >= tabs.size()) {
            currentTab = tabs.size() - 1;
        }
        switchToTab(currentTab);
    }

    private WebView createConfiguredWebView(boolean desktopMode) {
        SdrWebView wv = new SdrWebView(this);
        WebSettings ws = wv.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setSupportZoom(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        applyDesktopSettings(ws, desktopMode);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri != null ? uri.getScheme() : null;
                if (scheme != null && !scheme.equals("http") && !scheme.equals("https")
                        && !scheme.equals("about") && !scheme.equals("data") && !scheme.equals("blob")) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (Exception ignored) {
                    }
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                updateTabMetadata(view, url, null);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                updateTabMetadata(view, url, view.getTitle());
                String sdrHookJs =
                        "(function(){" +
                        "  var inputs = document.querySelectorAll('input');" +
                        "  inputs.forEach(function(el){" +
                        "    if(!el.getAttribute('enterkeyhint')) el.setAttribute('enterkeyhint','go');" +
                        "    if(!el.dataset.zionHooked){" +
                        "      el.dataset.zionHooked='1';" +
                        "      el.addEventListener('keydown',function(e){" +
                        "        if(e.key==='Enter'||e.keyCode===13){" +
                        "          el.dispatchEvent(new Event('change',{bubbles:true}));" +
                        "        }" +
                        "      });" +
                        "    }" +
                        "  });" +
                        "})();";
                view.evaluateJavascript(sdrHookJs, null);
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                int idx = findTabIndexByWebView(view);
                String failedUrl = (idx >= 0) ? tabs.get(idx).url : DEFAULT_HOME;
                boolean desk = (idx >= 0) && tabs.get(idx).desktopMode;
                if (view.getParent() instanceof ViewGroup) {
                    ((ViewGroup) view.getParent()).removeView(view);
                }
                view.destroy();
                if (idx >= 0) {
                    WebView replacement = createConfiguredWebView(desk);
                    tabs.get(idx).webView = replacement;
                    if (idx == currentTab) {
                        switchToTab(idx);
                    }
                    replacement.loadUrl(failedUrl);
                }
                Toast.makeText(ZionAubeBrowserMainActivity.this,
                        "Tab renderer recovered", Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView view, String title) {
                updateTabMetadata(view, view.getUrl(), title);
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback,
                                             FileChooserParams fileChooserParams) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;
                try {
                    Intent intent = fileChooserParams.createIntent();
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE);
                } catch (Exception e) {
                    filePathCallback = null;
                    Toast.makeText(ZionAubeBrowserMainActivity.this,
                            "Unable to open file picker", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });

        wv.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimetype, long contentLength) {
                try {
                    DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                    String filename = URLUtil.guessFileName(url, contentDisposition, mimetype);
                    req.setTitle(filename);
                    req.setDescription("Downloading via Bloatless");
                    req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
                    String cookies = CookieManager.getInstance().getCookie(url);
                    if (cookies != null) req.addRequestHeader("Cookie", cookies);
                    if (userAgent != null) req.addRequestHeader("User-Agent", userAgent);
                    DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    if (dm != null) {
                        dm.enqueue(req);
                        Toast.makeText(ZionAubeBrowserMainActivity.this,
                                "Downloading " + filename, Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(ZionAubeBrowserMainActivity.this,
                            "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });

        return wv;
    }

    private int findTabIndexByWebView(WebView view) {
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).webView == view) return i;
        }
        return -1;
    }

    private void updateTabMetadata(WebView view, String url, String title) {
        int idx = findTabIndexByWebView(view);
        if (idx < 0) return;
        TabEntry entry = tabs.get(idx);
        if (url != null && !url.isEmpty()) entry.url = url;
        if (title != null && !title.isEmpty()) entry.title = title;
        if (idx == currentTab && url != null && !urlInput.hasFocus()) {
            urlInput.setText(url);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (filePathCallback != null) {
                Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        ArrayList<String> urls = new ArrayList<>();
        boolean[] desktopFlags = new boolean[tabs.size()];
        for (int i = 0; i < tabs.size(); i++) {
            TabEntry t = tabs.get(i);
            urls.add(t.url != null ? t.url : DEFAULT_HOME);
            desktopFlags[i] = t.desktopMode;
        }
        outState.putStringArrayList("tab_urls", urls);
        outState.putBooleanArray("tab_desktop", desktopFlags);
        outState.putInt("current_tab", currentTab);
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        }
        for (TabEntry t : tabs) {
            if (t.webView != null) {
                t.webView.destroy();
            }
        }
        tabs.clear();
        super.onDestroy();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && urlInput != null) {
            imm.hideSoftInputFromWindow(urlInput.getWindowToken(), 0);
        }
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}
