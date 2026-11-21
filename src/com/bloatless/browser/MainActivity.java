package com.bloatless.browser;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
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
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final String DESKTOP_UA =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private static final String HOME_URL = "https://duckduckgo.com";

    private EditText urlInput;
    private Button desktopBtn;
    private LinearLayout tabBar;
    private FrameLayout webContainer;

    private final ArrayList<WebView> tabs = new ArrayList<>();
    private final ArrayList<Boolean> desktopModes = new ArrayList<>();
    private int currentTab = -1;
    private String defaultMobileUA;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#121212"));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets sys = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.ime()
                );
                v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
                return insets;
            });
        }

        // Top branding bar: Bloatless + developed by Zion Aube
        LinearLayout brandBar = new LinearLayout(this);
        brandBar.setOrientation(LinearLayout.HORIZONTAL);
        brandBar.setGravity(Gravity.CENTER_VERTICAL);
        brandBar.setPadding(16, 8, 16, 4);
        brandBar.setBackgroundColor(Color.parseColor("#181818"));

        TextView titleView = new TextView(this);
        titleView.setText("Bloatless");
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(15f);
        brandBar.addView(titleView, new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f
        ));

        TextView devView = new TextView(this);
        devView.setText("developed by Zion Aube");
        devView.setTextColor(Color.parseColor("#90CAF9"));
        devView.setTextSize(12f);
        brandBar.addView(devView);
        root.addView(brandBar);

        // Address & action bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(12, 8, 12, 8);
        topBar.setBackgroundColor(Color.parseColor("#1E1E1E"));

        urlInput = new EditText(this);
        urlInput.setHint("Search or enter URL");
        urlInput.setHintTextColor(Color.GRAY);
        urlInput.setTextColor(Color.WHITE);
        urlInput.setBackgroundColor(Color.parseColor("#2C2C2C"));
        urlInput.setPadding(20, 14, 20, 14);
        urlInput.setSingleLine(true);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlInput.setSelectAllOnFocus(true);
        urlInput.setContentDescription("Address bar");
        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                loadFromBar();
                return true;
            }
            return false;
        });

        LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f
        );
        urlParams.setMargins(0, 0, 10, 0);
        topBar.addView(urlInput, urlParams);

        Button goBtn = makeSmallButton("Go", "Load URL");
        goBtn.setOnClickListener(v -> loadFromBar());
        topBar.addView(goBtn);

        desktopBtn = makeSmallButton("PC", "Toggle Desktop Mode");
        desktopBtn.setOnClickListener(v -> toggleDesktopMode());
        topBar.addView(desktopBtn);

        Button newTabBtn = makeSmallButton("+", "New Tab");
        newTabBtn.setOnClickListener(v -> addNewTab(HOME_URL));
        topBar.addView(newTabBtn);

        root.addView(topBar);

        // Horizontal tab strip (tap to switch, long-press to close)
        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setBackgroundColor(Color.parseColor("#181818"));
        tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setPadding(8, 4, 8, 6);
        tabScroll.addView(tabBar);
        root.addView(tabScroll);

        // Main WebView container
        webContainer = new FrameLayout(this);
        root.addView(webContainer, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f
        ));

        setContentView(root);

        String initialUrl = HOME_URL;
        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            initialUrl = intent.getData().toString();
        }
        addNewTab(initialUrl);
    }

    private Button makeSmallButton(String label, String desc) {
        Button b = new Button(this);
        b.setText(label);
        b.setContentDescription(desc);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.parseColor("#333333"));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(22, 10, 22, 10);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(6, 0, 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void addNewTab(String url) {
        WebView wv = new WebView(this);
        WebSettings ws = wv.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setSupportZoom(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);

        if (defaultMobileUA == null) {
            defaultMobileUA = ws.getUserAgentString();
        }

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String loadedUrl) {
                if (tabs.indexOf(view) == currentTab && !urlInput.hasFocus()) {
                    urlInput.setText(loadedUrl);
                }
                refreshTabStrip();
            }
        });

        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView view, String title) {
                refreshTabStrip();
            }
        });

        tabs.add(wv);
        desktopModes.add(false);
        switchToTab(tabs.size() - 1);
        wv.loadUrl(url);
    }

    private void switchToTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        currentTab = index;
        webContainer.removeAllViews();
        WebView active = tabs.get(currentTab);
        webContainer.addView(active, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
        String currentUrl = active.getUrl();
        urlInput.setText(currentUrl != null ? currentUrl : "");
        updateDesktopButtonVisual();
        refreshTabStrip();
    }

    private void closeTab(int index) {
        if (tabs.size() <= 1) {
            tabs.get(0).loadUrl(HOME_URL);
            return;
        }
        WebView closing = tabs.remove(index);
        desktopModes.remove(index);
        closing.destroy();
        if (currentTab >= tabs.size()) {
            currentTab = tabs.size() - 1;
        }
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

            TextView tabChip = new TextView(this);
            tabChip.setText(title + "  ✕");
            tabChip.setTextColor(i == currentTab ? Color.BLACK : Color.WHITE);
            tabChip.setBackgroundColor(i == currentTab ? Color.parseColor("#90CAF9") : Color.parseColor("#2A2A2A"));
            tabChip.setPadding(22, 10, 22, 10);
            tabChip.setContentDescription("Tab " + (i + 1) + ": " + title + ". Tap to switch, long press to close.");

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(0, 0, 8, 0);
            tabChip.setLayoutParams(lp);

            tabChip.setOnClickListener(v -> switchToTab(idx));
            tabChip.setOnLongClickListener(v -> {
                closeTab(idx);
                return true;
            });
            tabBar.addView(tabChip);
        }
    }

    private void loadFromBar() {
        if (currentTab < 0) return;
        String raw = urlInput.getText().toString().trim();
        if (raw.isEmpty()) return;

        String target;
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            target = raw;
        } else if (!raw.contains(" ") && raw.contains(".")) {
            target = "https://" + raw;
        } else {
            target = "https://duckduckgo.com/?q=" + Uri.encode(raw);
        }

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

        updateDesktopButtonVisual();
        wv.reload();
    }

    private void updateDesktopButtonVisual() {
        boolean isDesktop = currentTab >= 0 && desktopModes.get(currentTab);
        desktopBtn.setBackgroundColor(isDesktop ? Color.parseColor("#2E7D32") : Color.parseColor("#333333"));
        desktopBtn.setContentDescription(isDesktop ? "Desktop Mode On" : "Desktop Mode Off");
    }

    @Override
    public void onBackPressed() {
        if (currentTab >= 0 && tabs.get(currentTab).canGoBack()) {
            tabs.get(currentTab).goBack();
        } else if (tabs.size() > 1) {
            closeTab(currentTab);
        } else {
            super.onBackPressed();
        }
    }

    private void openInBloatless() {
        try {
            String targetUrl = null;
            for (java.lang.reflect.Field field : getClass().getDeclaredFields()) {
                if (android.webkit.WebView.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    Object obj = field.get(this);
                    if (obj instanceof android.webkit.WebView) {
                        targetUrl = ((android.webkit.WebView) obj).getUrl();
                        break;
                    }
                }
            }
            if ((targetUrl == null || targetUrl.trim().isEmpty()) && getIntent() != null && getIntent().getData() != null) {
                targetUrl = getIntent().getData().toString();
            }
            android.content.Intent launchIntent = new android.content.Intent(this, MainActivity.class);
            launchIntent.setAction(android.content.Intent.ACTION_VIEW);
            if (targetUrl != null && !targetUrl.trim().isEmpty()) {
                launchIntent.setData(android.net.Uri.parse(targetUrl));
            }
            launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(launchIntent);
        } catch (Throwable t) {
            android.widget.Toast.makeText(this, "Unable to open in Bloatless", android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
