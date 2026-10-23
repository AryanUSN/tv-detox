package com.ansub.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.AudioManager;
import android.media.audiofx.Equalizer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.List;

public class MainActivity extends Activity {

    // Top-row apps, in this order of preference:
    //  1. a list saved at install time:  am start -n com.ansub.tvhome/.MainActivity --es apps "pkg1,pkg2"
    //     (--es apps auto  goes back to 2; add --es stamp <unique> so it's applied only once, see saveConfig)
    //  2. apps.txt (built in as Apps.LIST), topped up with other TV apps installed on this TV.
    // Missing apps are skipped. Topping up stops at six apps, but a chosen list can be any length:
    // apps wrap into rows of six, like Apple TV, and the page scrolls down once they don't all fit.
    // On the TV, hold OK on an app (or press Menu) to move or remove it, or add another; that saves list 1.
    // A network tile always sits at the top right, level with the clock. Settings shortcuts can join
    // it there, off until picked the same way:
    //   --es quick "bluetooth,sound,picture"
    // (--es quick none  takes them away again; "wifi" and "ethernet" are the network tile now).
    private static final String PREFS = "config";
    private static final List<String> NOT_APPS = Arrays.asList("com.android.vending", "com.android.tv.settings",
            "com.android.tv", "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx",
            "com.google.android.leanbacklauncher");
    private static final String PLAY_STORE = "com.android.vending";
    private static final String SETTINGS = "settings";
    private static final String ADD = "add";
    private static final String[] EDIT_ITEMS = {"Move", "Replace", "Remove from home", "Uninstall"};
    private static final String[] EDIT_ITEMS_SYSTEM = {"Move", "Replace", "Remove from home", "App info"};
    private static final int PER_ROW = 6, MAX_INPUTS = 4;

    // Settings shortcuts: name, label, icon. Tile keys are QUICK + name.
    private static final String QUICK = "quick:";
    private static final String[] QUICK_NAMES = {"bluetooth", "sound", "picture"};
    private static final String[] QUICK_LABELS = {"Bluetooth", "Sound", "Picture"};
    private static final int[][] QUICK_ICONS = {LineIcon.BLUETOOTH, LineIcon.SOUND, LineIcon.PICTURE};
    private static final String NETWORK = "network";
    private static final int OFFLINE = 0, ETHERNET = 4; // network states; 1 to 3 are Wi-Fi bars
    // TCL keeps its own Picture and Sound panels in a service; Android's Sound page is missing there.
    private static final String TCL_SHOW_WINDOW = "com.tcl.settings.SHOW_WINDOW";

    // Layout is designed at 1920x1080 and scaled to the TV's real UI size by px().
    private static final int MARGIN = 120, RADIUS = 24;
    // The apps sit in one row, centred on a glass shelf. Tile size and gap, where the shelf sits and how
    // big it is, how far down inside it the tiles start, and how far the others slide from the focused one.
    private static final int TILE_W = 200, TILE_H = 112, TILE_GAP = 26;
    private static final int ROW_TOP = 628, ROW_H = 268, SHELF_W = 1680, TILE_IN = 78, PUSH = 22;
    private static final float POP_SCALE = 1.18f; // how much bigger the focused app grows
    private static final int ICON_TOP = 68, ICON_BUTTON = 64, ICON_SIZE = 34, ICON_GAP = 8; // top right
    // The tab bar at the top: what the shelf shows.
    private static final String TAB = "tab:", SET = "set:";
    private static final String[] TAB_NAMES = {"Apps", "Inputs", "Settings"};
    private static final String[] SOUND_MODES = {"Standard", "Movie", "Music", "Voice"};
    private static final String[] PIC_MODES = {"Standard", "Vivid", "Sport", "Movie", "Custom"};

    // Wallpapers, saved by name. Each one is a scene: five sky colours top to bottom, a glow at the top
    // left, a glow on the horizon, and the colour of the dunes at their top and bottom.
    private static final String[] WALLPAPER_NAMES = {"Dusk", "Ocean", "Dunes", "Aurora", "Rose", "Midnight", "Graphite"};
    private static final int[][] WALLPAPERS = {
        {0xFF1C2A66, 0xFF59489B, 0xFFD9788F, 0xFFF4AE78, 0xFF35213F, 0x8C7896FF, 0xD9FFC478, 0x8C5A285A, 0xE61E1028},
        {0xFF06243A, 0xFF0B4F6E, 0xFF1E86A0, 0xFF7FD0C4, 0xFF0A2A33, 0x8C4AA8FF, 0xD9B8FFE8, 0x8C0E4A5A, 0xE6051A22},
        {0xFF3A1E4A, 0xFF9A4A5E, 0xFFE58A5A, 0xFFFFC47A, 0xFF4A2A22, 0x8CFF9A6A, 0xD9FFE0A0, 0x8C8A3A2E, 0xE62A1410},
        {0xFF04101C, 0xFF0A2E3A, 0xFF0F5A52, 0xFF2A9A7A, 0xFF06161A, 0x8C58FFB0, 0xD98C5AF0, 0x8C0A3A38, 0xE6030C0C},
        {0xFF3A1040, 0xFF8A2A6E, 0xFFD8508A, 0xFFFF9A9A, 0xFF3A1030, 0x8CFF8AD0, 0xD9FFC8A0, 0x8C7A2058, 0xE6200820},
        {0xFF070A1A, 0xFF101E4A, 0xFF24307A, 0xFF4A4AA0, 0xFF0A0A1E, 0x8C5A7AFF, 0xD98A78FF, 0x8C1A1E4E, 0xE6050618},
        {0xFF0C0D0F, 0xFF1A1C20, 0xFF2A2D33, 0xFF4A4F58, 0xFF0C0D0F, 0x66FFFFFF, 0xA0C8D2E0, 0x8C26282C, 0xE6060708},
    };
    private float scale = 1f;

    // Focus pops with a small overshoot and settles; letting go eases back. These are one-shot
    // animations only, so nothing runs while the TV sits idle.
    private static final Interpolator POP = new OvershootInterpolator(1.7f);
    private static final Interpolator EASE = new DecelerateInterpolator(1.5f);
    private boolean entered; // the tiles have already risen into place once
    private boolean intro;   // the first spring-up is still playing

    private static Typeface regular, semibold, light;

    private FrameLayout root; // the page, inside a vertical scroller
    private int tileW, tileH; // app tile size, set by buildTiles
    private HorizontalScrollView appsScroll; // the sideways app row...
    private FrameLayout appsRow;             // ...and what's inside it
    private LinearLayout dock;               // inputs, Settings and Add app
    private TextView sectionLabel, nameText, countText, credit;
    // The soft glow behind the apps. It takes the colour of the focused app and moves to sit behind it.
    private GradientDrawable tintGlow;
    private ValueAnimator tintAnim;
    private int tintColor;
    private float tintX = 0.22f;
    private final ArgbEvaluator argb = new ArgbEvaluator();
    private final HashMap<String, Integer> tints = new HashMap<>(); // glow colour per app
    private final HashMap<String, String> names = new HashMap<>();  // what the header says per tile
    private final List<View> tiles = new ArrayList<>();
    private boolean dirty = true;
    private java.util.Locale shownLocale; // the TV's language (and region) when the page was built
    private String focusedKey;
    private final List<String> rowApps = new ArrayList<>(); // the top row as shown, in order
    private String movingKey;   // the app being moved with left/right, if any
    private TextView moveHint;  // shown above the apps while moving
    private boolean longPressed; // OK was held: swallow the key-up so the app doesn't open
    // The tile OK went down on. An app that closes on OK's key-down (Netflix's "Exit") hands the matching
    // key-up to the tile still focused here, so a key-up only opens the app it went down on (#30).
    private String okDownKey;

    private int mode;        // the tab: 0 Apps, 1 Inputs, 2 Settings
    private int rowLeft;     // where the first tile sits inside the shelf (the row is centred)
    private View capsule;    // the glass pill behind the round icons, top right
    private TextView wxCity, wxTemp, wxCond, wxRange;
    private final TextView[] wxHour = new TextView[6];
    private final TextView[] wxHourT = new TextView[6];
    private final ImageView[] wxIcon = new ImageView[6];
    private Slider brightSlider, volSlider;
    private TextView brightPct, volPct;
    private final List<TextView> chips = new ArrayList<>();
    private final List<GradientDrawable> chipBgs = new ArrayList<>();
    private final List<TextView> picChips = new ArrayList<>();
    private final List<GradientDrawable> picBgs = new ArrayList<>();
    private int soundMode;
    private Equalizer eq;
    private boolean eqChecked, eqOk, warnedWrite;
    private final Runnable wxTick = new Runnable() {
        @Override public void run() {
            loadWeather(true);
            if (root != null) root.postDelayed(this, 30 * 60 * 1000L);
        }
    };

    // Apps installed, removed or updated. This often lands once Home is already back in front (an app
    // uninstalled from the menu is gone only after Android's prompt has closed), so catch up then.
    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            dirty = true;
            refreshApps();
        }
    };

    // The network tile follows the connection live: Wi-Fi bars, Ethernet or Offline. Only its icon and
    // label change, so nothing is rebuilt.
    private ImageView netIcon;
    private int netState = -1;
    private final ConnectivityManager.NetworkCallback networkChanged = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network n) { runOnUiThread(MainActivity.this::refreshNetwork); }
        @Override public void onLost(Network n) { runOnUiThread(MainActivity.this::refreshNetwork); }
        // Also fires when the Wi-Fi signal gets stronger or weaker.
        @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) {
            runOnUiThread(MainActivity.this::refreshNetwork);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        saveConfig(getIntent());
        loadFonts();
        scale = getResources().getDisplayMetrics().widthPixels / 1920f;
        showWallpaper(wallpaper());

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.setClipChildren(false);
        root = new FrameLayout(this);
        root.setClipChildren(false);
        scroller.addView(root, new FrameLayout.LayoutParams(-1, -2));

        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        shownLocale = java.util.Locale.getDefault();
        TextClock time = clock("h:mm", "H:mm", px(132), Color.WHITE, light);
        time.setLetterSpacing(-0.04f);
        time.setFontFeatureSettings("tnum");
        time.setIncludeFontPadding(false);
        time.setShadowLayer(px(30), 0, px(6), 0x59140A32);
        clock.addView(time);
        String day = Strings.datePattern();
        TextClock date = clock(day, day, px(28), 0xE0FFFFFF, regular);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-2, -2);
        dp.topMargin = px(2);
        clock.addView(date, dp);
        root.addView(clock, at(px(MARGIN), px(44), -2, -2));
        clock.setAlpha(0f);
        clock.animate().alpha(1f).setDuration(600).setInterpolator(EASE).start();

        moveHint = text(px(22), 0xCCFFFFFF, regular);
        moveHint.setText(Strings.t("Use the arrows to move it, then press OK"));
        moveHint.setVisibility(View.GONE);
        root.addView(moveHint, at(px(MARGIN), px(592), -2, -2));

        buildChrome();
        soundMode = getSharedPreferences(PREFS, 0).getInt("sound", 0);
        if (soundMode != 0) applySound(soundMode);
        root.postDelayed(wxTick, 30 * 60 * 1000L);

        setContentView(scroller);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(packagesChanged, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(packagesChanged, f);

        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) try {
            if (android.os.Build.VERSION.SDK_INT >= 24) cm.registerDefaultNetworkCallback(networkChanged);
            else cm.registerNetworkCallback(new NetworkRequest.Builder().build(), networkChanged);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        if (root != null) root.removeCallbacks(wxTick);
        if (eq != null) try { eq.release(); } catch (Exception ignored) {}
        unregisterReceiver(packagesChanged);
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) try { cm.unregisterNetworkCallback(networkChanged); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (saveConfig(intent)) buildTiles();
        if (credit != null) credit.setText(creditText());
    }

    // Saves an app list passed with --es apps "..." and shortcuts passed with --es quick "...".
    // Returns true if it changed.
    // When Android kills the home screen in the background, it can start it again with the same old
    // intent; the stamp install.sh sends stops that from undoing changes made on the TV since.
    private boolean saveConfig(Intent intent) {
        String apps = intent == null ? null : intent.getStringExtra("apps");
        String quick = intent == null ? null : intent.getStringExtra("quick");
        String small = intent == null ? null : intent.getStringExtra("credit");
        String where = intent == null ? null : intent.getStringExtra("city");
        String pics = intent == null ? null : intent.getStringExtra("picmodes");
        if (apps == null && quick == null && small == null && where == null && pics == null) return false;
        SharedPreferences prefs = getSharedPreferences(PREFS, 0);
        String stamp = intent.getStringExtra("stamp");
        if (stamp != null && stamp.equals(prefs.getString("stamp", null))) return false;
        SharedPreferences.Editor e = prefs.edit();
        if (stamp != null) e.putString("stamp", stamp);
        if (apps != null) {
            apps = apps.trim();
            if (apps.isEmpty() || apps.equalsIgnoreCase("auto")) e.remove("apps");
            else e.putString("apps", apps);
        }
        if (quick != null) {
            quick = quick.trim();
            if (quick.isEmpty() || quick.equalsIgnoreCase("none")) e.remove("quick");
            else e.putString("quick", quick);
        }
        if (small != null) {
            small = small.trim();
            if (small.isEmpty()) e.remove("credit");
            else e.putString("credit", small);
        }
        if (pics != null) {
            String[] pm = pics.trim().split("[,\\s]+");
            for (int i = 0; i < pm.length && i < PIC_MODES.length; i++) {
                try { e.putInt("pm_" + i, Integer.parseInt(pm[i])); } catch (Exception ignored) {}
            }
        }
        String city = intent.getStringExtra("city");
        if (city != null) {
            city = city.trim();
            if (city.isEmpty()) e.remove("city");
            else e.putString("city", city);
        }
        e.apply();
        dirty = true;
        return true;
    }

    private List<String> appList(PackageManager pm) {
        List<String> out = new ArrayList<>();
        String saved = getSharedPreferences(PREFS, 0).getString("apps", null);
        String[] wanted = saved != null ? saved.split("[,\\s]+") : Apps.LIST;
        for (String p : wanted) if (!p.isEmpty() && !out.contains(p) && launchIntent(pm, p) != null) out.add(p);
        if (saved == null) {
            // Top up with other TV apps people installed (or updated), skipping launchers and system tools.
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                if (out.size() >= PER_ROW) break;
                String p = ri.activityInfo.packageName;
                int flags = ri.activityInfo.applicationInfo.flags;
                boolean stockSystem = (flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        && (flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
                if (stockSystem || out.contains(p) || NOT_APPS.contains(p) || p.equals(getPackageName())) continue;
                out.add(p);
            }
        }
        return out;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Rebuild only when apps were installed, removed or updated.
        if (dirty) {
            dirty = false;
            buildTiles();
        }
        refreshNetwork();
        syncSliders();
        loadWeather(false);
        View f = getCurrentFocus();
        if (f == null && !tiles.isEmpty()) tiles.get(0).requestFocus();
        // On the first start the page isn't attached to the window yet, so Android doesn't announce the
        // first focus; catch up once it is.
        root.post(this::syncFocus);
    }

    // Back from a menu or a window on top (like the uninstall prompt): catch up on apps that changed.
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) refreshApps();
        else okDownKey = null; // an OK still held finishes in the window that took over
    }

    // Rebuilds the tiles if apps changed, but only while Home is in front with no menu open and no
    // app being moved; otherwise the next onResume, focus change or end of the move does it.
    private void refreshApps() {
        if (!dirty || !hasWindowFocus() || movingKey != null) return;
        dirty = false;
        buildTiles();
    }

    // The TV's language changed: start over so the labels and the date follow it.
    @Override
    public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        if (!java.util.Locale.getDefault().equals(shownLocale)) recreate();
    }

    @Override
    public void onBackPressed() { /* home screen: nothing to go back to */ }

    // The wallpaper is drawn once into a small bitmap (a sky, two glows, two dunes) and the window just
    // stretches it, so it costs nothing while the TV sits idle. Smooth gradients stay smooth stretched.
    private Drawable background(int[] c) {
        int w = 960, h = 540;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        float k = w / 1920f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h, new int[] {c[0], c[1], c[2], c[3], c[4]},
                new float[] {0f, 0.36f, 0.66f, 0.84f, 1f}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        p.setShader(new RadialGradient(0.15f * w, 0f, 0.55f * w, c[5], c[5] & 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        p.setShader(new RadialGradient(0.5f * w, 1.08f * h, 0.6f * w, c[6], c[6] & 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        p.setShader(new LinearGradient(0, 820 * k, 0, h, c[7], c[8], Shader.TileMode.CLAMP));
        cv.drawOval(new RectF(-200 * k, 820 * k, 1300 * k, 1340 * k), p);
        cv.drawOval(new RectF(700 * k, 880 * k, 2300 * k, 1360 * k), p);
        // A little darkness at the top and bottom keeps the white text easy to read.
        p.setShader(new LinearGradient(0, 0, 0, h, new int[] {0x47080614, 0x0D080614, 0x73080614},
                new float[] {0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        BitmapDrawable d = new BitmapDrawable(getResources(), b);
        d.setFilterBitmap(true);
        return d;
    }

    // The saved wallpaper, as an index into WALLPAPERS (Dusk if none is saved).
    private int wallpaper() {
        String saved = getSharedPreferences(PREFS, 0).getString("wallpaper", null);
        return Math.max(0, Arrays.asList(WALLPAPER_NAMES).indexOf(saved));
    }

    private void showWallpaper(int i) {
        getWindow().setBackgroundDrawable(background(WALLPAPERS[i]));
    }

    // The wallpapers, each with a small dot of its colours. Moving through the list shows each one
    // behind the panel; OK keeps it, Back goes back to the one before.
    private void showWallpapers() {
        final int size = px(40);
        panel(Strings.t("Wallpaper"), Arrays.asList(WALLPAPER_NAMES), pos -> {
            int[] c = WALLPAPERS[pos];
            GradientDrawable dot = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {c[0], c[2], c[3]});
            dot.setShape(GradientDrawable.OVAL);
            dot.setStroke(Math.max(1, px(2)), 0x40FFFFFF);
            dot.setSize(size, size);
            return dot;
        }, size, wallpaper(), this::showWallpaper, which -> {
            getSharedPreferences(PREFS, 0).edit().putString("wallpaper", WALLPAPER_NAMES[which]).apply();
            showWallpaper(which);
        });
    }

    // A glow colour made solid over the base, three times as strong, so a small dot shows it.
    private static int glow(int glow, int base) {
        float a = Math.min(1f, 3 * (glow >>> 24) / 255f);
        int r = Math.round(Color.red(base) + (Color.red(glow) - Color.red(base)) * a);
        int g = Math.round(Color.green(base) + (Color.green(glow) - Color.green(base)) * a);
        int b = Math.round(Color.blue(base) + (Color.blue(glow) - Color.blue(base)) * a);
        return Color.rgb(r, g, b);
    }

    private static GradientDrawable radial(int color, float cx, float cy, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setColors(new int[] {color, color & 0x00FFFFFF});
        g.setGradientCenter(cx, cy);
        g.setGradientRadius(r);
        return g;
    }

    // A frosted-glass look that costs nothing to draw: a see-through diagonal fill and a bright edge.
    private GradientDrawable glass(int radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0x47FFFFFF, 0x14FFFFFF, 0x29FFFFFF});
        g.setCornerRadius(px(radius));
        g.setStroke(Math.max(1, px(2)), 0x47FFFFFF);
        return g;
    }

    // The glass (tab bar, icon pill, weather, quick controls, app shelf), the sideways app row on the
    // shelf, the name under it and the small print.
    private void buildChrome() {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int m = px(MARGIN), cw = px(SHELF_W);

        View bar = new View(this);
        bar.setBackground(glass(36));
        root.addView(bar, at((screenW - px(552)) / 2, px(64), px(552), px(72)));
        capsule = new View(this);
        capsule.setBackground(glass(38));
        root.addView(capsule, at(0, px(62), px(100), px(76)));
        buildWeather(m);
        buildControls(m + px(1090));
        View shelf = new View(this);
        shelf.setBackground(glass(52));
        root.addView(shelf, at(m, px(ROW_TOP), cw, px(ROW_H)));

        // Not shown any more, but the focus code still fills them in.
        sectionLabel = text(px(20), 0x9EFFFFFF, semibold);
        countText = text(px(20), 0x9EFFFFFF, semibold);

        // The name of the focused app, under the row.
        nameText = text(px(24), 0xE6FFFFFF, semibold);
        nameText.setSingleLine(true);
        nameText.setGravity(Gravity.CENTER);
        root.addView(nameText, at(m, px(ROW_TOP + 214), cw, -2));

        // The apps: one row that slides sideways when there are more than fit.
        appsScroll = new HorizontalScrollView(this);
        appsScroll.setHorizontalScrollBarEnabled(false);
        appsScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        appsScroll.setClipChildren(false);
        appsScroll.setClipToPadding(false);
        appsScroll.setHorizontalFadingEdgeEnabled(true);
        appsScroll.setFadingEdgeLength(px(80));
        appsRow = new FrameLayout(this);
        appsRow.setClipChildren(false);
        appsScroll.addView(appsRow, new FrameLayout.LayoutParams(-2, -1));
        root.addView(appsScroll, at(m, px(ROW_TOP), cw, px(ROW_H)));

        // The small print, centred at the bottom: press OK for a surprise, hold OK to change the text.
        credit = text(px(20), 0x99FFFFFF, regular);
        credit.setText(creditText());
        credit.setSingleLine(true);
        credit.setGravity(Gravity.CENTER);
        credit.setFocusable(true);
        credit.setPadding(px(12), px(8), px(12), px(8));
        credit.setOnClickListener(v -> surprise());
        credit.setOnLongClickListener(v -> { editCredit(); return true; });
        credit.setOnFocusChangeListener((v, has) -> {
            credit.setTextColor(has ? 0xFFFFFFFF : 0x99FFFFFF);
            credit.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).setStartDelay(0)
                    .setDuration(has ? 240 : 160).setInterpolator(has ? POP : EASE).start();
        });
        root.addView(credit, at((screenW - px(420)) / 2, px(1004), px(420), -2));
        credit.setAlpha(0f);
        credit.animate().alpha(1f).setStartDelay(1100).setDuration(700).start();

        // One place that follows focus: the name under the row and how the row opens up.
        root.getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> onFocusMoved(newF));
    }

    // The weather card: the temperature now, then the next hours. Filled in when the forecast arrives.
    private void buildWeather(int x) {
        int y = px(262);
        View card = new View(this);
        final GradientDrawable cardBg = glass(44);
        card.setBackground(cardBg);
        card.setFocusable(true);
        card.setOnFocusChangeListener((v, has) ->
                cardBg.setStroke(has ? Math.max(2, px(4)) : Math.max(1, px(2)), has ? 0xFFFFFFFF : 0x47FFFFFF));
        card.setOnClickListener(v -> showCities());
        root.addView(card, at(x, y, px(1050), px(318)));
        TextView change = text(px(18), 0x99FFFFFF, regular);
        change.setText("OK to change place");
        change.setGravity(Gravity.END);
        root.addView(change, at(x + px(790), y + px(40), px(220), -2));
        wxCity = text(px(24), 0xE6FFFFFF, semibold);
        wxCity.setLetterSpacing(0.12f);
        wxCity.setText(city()[0].toUpperCase(java.util.Locale.ROOT));
        root.addView(wxCity, at(x + px(52), y + px(36), -2, -2));
        wxTemp = text(px(150), Color.WHITE, light);
        wxTemp.setIncludeFontPadding(false);
        wxTemp.setLetterSpacing(-0.05f);
        wxTemp.setText("--°");
        root.addView(wxTemp, at(x + px(46), y + px(70), -2, -2));
        wxCond = text(px(28), Color.WHITE, semibold);
        wxCond.setSingleLine(true);
        wxCond.setText("No forecast yet");
        root.addView(wxCond, at(x + px(52), y + px(232), px(290), -2));
        wxRange = text(px(21), 0xCCFFFFFF, regular);
        wxRange.setSingleLine(true);
        wxRange.setEllipsize(android.text.TextUtils.TruncateAt.END);
        root.addView(wxRange, at(x + px(52), y + px(272), px(296), -2));
        View div = new View(this);
        div.setBackgroundColor(0x33FFFFFF);
        root.addView(div, at(x + px(360), y + px(50), Math.max(1, px(2)), px(218)));
        TextView hdr = text(px(21), 0xC7FFFFFF, semibold);
        hdr.setLetterSpacing(0.14f);
        hdr.setText("NEXT HOURS");
        root.addView(hdr, at(x + px(408), y + px(44), -2, -2));
        for (int i = 0; i < 6; i++) {
            int cx = x + px(408) + i * px(104);
            wxHour[i] = text(px(23), 0xE0FFFFFF, semibold);
            wxHour[i].setGravity(Gravity.CENTER);
            root.addView(wxHour[i], at(cx, y + px(104), px(96), -2));
            wxIcon[i] = new ImageView(this);
            root.addView(wxIcon[i], at(cx + px(26), y + px(152), px(44), px(44)));
            wxHourT[i] = text(px(28), Color.WHITE, semibold);
            wxHourT[i].setGravity(Gravity.CENTER);
            root.addView(wxHourT[i], at(cx, y + px(214), px(96), -2));
        }
    }

    // The quick controls card: brightness, volume, the picture mode and the sound mode.
    private void buildControls(int x) {
        int y = px(262), w = px(502), cx = x + px(44);
        View card = new View(this);
        card.setBackground(glass(44));
        root.addView(card, at(x, y, px(590), px(318)));
        TextView hdr = text(px(20), 0xC7FFFFFF, semibold);
        hdr.setLetterSpacing(0.14f);
        hdr.setText("QUICK CONTROLS");
        root.addView(hdr, at(cx, y + px(26), -2, -2));

        TextView bl = text(px(23), Color.WHITE, semibold);
        bl.setText("Brightness");
        root.addView(bl, at(cx, y + px(54), -2, -2));
        brightPct = text(px(23), 0xCCFFFFFF, semibold);
        brightPct.setGravity(Gravity.END);
        root.addView(brightPct, at(cx, y + px(54), w, -2));
        brightSlider = new Slider(this, new LineIcon(LineIcon.SUN), v -> setBrightness(v));
        root.addView(brightSlider, at(cx, y + px(82), w, px(52)));

        // The picture mode: five chips. Hold OK on one to save the TV's current mode to it.
        int g = px(8), pn = PIC_MODES.length, pw = (w - (pn - 1) * g) / pn;
        for (int i = 0; i < pn; i++) addPicChip(PIC_MODES[i], cx + i * (pw + g), y + px(136), pw, i);


        TextView vl = text(px(23), Color.WHITE, semibold);
        vl.setText("Volume");
        root.addView(vl, at(cx, y + px(184), -2, -2));
        volPct = text(px(23), 0xCCFFFFFF, semibold);
        volPct.setGravity(Gravity.END);
        root.addView(volPct, at(cx, y + px(184), w, -2));
        volSlider = new Slider(this, new LineIcon(LineIcon.SOUND), v -> setVolume(v));
        root.addView(volSlider, at(cx, y + px(212), w, px(52)));

        

        // The sound mode: four chips, or one button to the TV's own sound page when this TV can't do it.
        int cy = y + px(274);
        soundMode = getSharedPreferences(PREFS, 0).getInt("sound", 0);
        if (!soundSupported()) {
            addChip("More sound settings", cx, cy, w, -1);
        } else {
            int n = SOUND_MODES.length, cwid = (w - (n - 1) * g) / n;
            for (int i = 0; i < n; i++) addChip(SOUND_MODES[i], cx + i * (cwid + g), cy, cwid, i);
        }
        paintChips();
        syncSliders();
    }

    private void addPicChip(String label, int x, int y, int w, final int idx) {
        final TextView t = text(px(17), Color.WHITE, semibold);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setFocusable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(20));
        t.setBackground(bg);
        t.setOnFocusChangeListener((v, has) -> {
            paintPicChips();
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setStartDelay(0)
                    .setDuration(has ? 240 : 160).setInterpolator(has ? POP : EASE).start();
        });
        t.setOnClickListener(v -> pickPicture(idx));
        t.setOnLongClickListener(v -> { learnPicture(idx); return true; });
        picChips.add(t);
        picBgs.add(bg);
        root.addView(t, at(x, y, w, px(40)));
    }

    // The TV's own number for a picture mode, saved by holding OK on its chip (or sent with --es picmodes).
    private int learnedPic(int idx) {
        return getSharedPreferences(PREFS, 0).getInt("pm_" + idx, -1);
    }

    private int currentPic() {
        try { return Settings.Global.getInt(getContentResolver(), "picture_mode"); }
        catch (Exception e) { return -1; }
    }

    private void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show();
    }

    private void pickPicture(int idx) {
        int v = learnedPic(idx);
        if (v < 0) {
            toast("First set " + PIC_MODES[idx] + " on the TV (Settings > Picture), then hold OK on this button to save it.");
            return;
        }
        try {
            Settings.Global.putInt(getContentResolver(), "picture_mode", v);
        } catch (Exception e) {
            toast("Needs the one-time permission: run the grant command from the PC.");
            return;
        }
        paintPicChips();
        root.postDelayed(this::syncSliders, 700);
    }

    private void learnPicture(int idx) {
        int cur = currentPic();
        if (cur < 0) return;
        getSharedPreferences(PREFS, 0).edit().putInt("pm_" + idx, cur).apply();
        toast(PIC_MODES[idx] + " saved");
        paintPicChips();
    }

    private void paintPicChips() {
        int cur = currentPic();
        for (int i = 0; i < picChips.size(); i++) {
            TextView t = picChips.get(i);
            boolean on = cur >= 0 && learnedPic(i) == cur;
            picBgs.get(i).setColor(on ? 0xFFFFFFFF : t.hasFocus() ? 0x59FFFFFF : 0x29FFFFFF);
            t.setTextColor(on ? 0xFF121214 : Color.WHITE);
        }
    }

    private void addChip(String label, int x, int y, int w, final int idx) {
        final TextView t = text(px(19), Color.WHITE, semibold);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setFocusable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(20));
        t.setBackground(bg);
        t.setOnFocusChangeListener((v, has) -> {
            paintChips();
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setStartDelay(0)
                    .setDuration(has ? 240 : 160).setInterpolator(has ? POP : EASE).start();
        });
        t.setOnClickListener(v -> {
            if (idx < 0) { openQuick("sound"); return; }
            soundMode = idx;
            getSharedPreferences(PREFS, 0).edit().putInt("sound", idx).apply();
            applySound(idx);
            paintChips();
        });
        chips.add(t);
        chipBgs.add(bg);
        root.addView(t, at(x, y, w, px(40)));
    }

    private void paintChips() {
        for (int i = 0; i < chips.size(); i++) {
            TextView t = chips.get(i);
            boolean on = soundSupported() && i == soundMode;
            chipBgs.get(i).setColor(on ? 0xFFFFFFFF : t.hasFocus() ? 0x59FFFFFF : 0x29FFFFFF);
            t.setTextColor(on ? 0xFF121214 : Color.WHITE);
        }
    }

    private void buildTiles() {
        // While tiles are swapped out, removing the focused one would hand focus down the row (and
        // overwrite focusedKey), so hold focus off until the new tiles are in.
        String want = focusedKey;
        int wantAt = rowApps.indexOf(want); // where it was, in case the app is gone now
        root.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        for (View t : tiles) {
            View wrap = (View) t.getParent();
            ((ViewGroup) wrap.getParent()).removeView(wrap);
        }
        tiles.clear();
        rowApps.clear();
        tints.clear();
        names.clear();
        movingKey = null;
        moveHint.setVisibility(View.GONE);

        PackageManager pm = getPackageManager();
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int m = px(MARGIN), g = px(TILE_GAP), w = px(TILE_W), h = px(TILE_H);
        tileW = w;
        tileH = h;
        root.setMinimumHeight(px(1080));

        // What the shelf shows depends on the tab. Apps are the apps; Inputs are Play Store and the HDMI
        // inputs; Settings are the home screen's own settings. All of them sit centred on the shelf.
        List<String> keys = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<int[]> lineIcons = new ArrayList<>();
        if (mode == 0) {
            for (String pkg : appList(pm)) {
                if (launchIntent(pm, pkg) == null) continue;
                keys.add(pkg);
                labels.add(labelOf(pkg));
                lineIcons.add(null);
            }
        } else if (mode == 1) {
            if (launchIntent(pm, PLAY_STORE) != null) {
                keys.add(PLAY_STORE);
                labels.add("Play Store");
                lineIcons.add(LineIcon.PLAY);
            }
            for (TvInputInfo in : hdmiInputs()) {
                keys.add(in.getId());
                labels.add(inputLabel(in));
                lineIcons.add(LineIcon.HDMI);
            }
        } else {
            String[][] items = {{"wallpaper", "Wallpaper"}, {"add", "Add app"}, {"city", "Weather place"},
                    {"tv", "TV settings"}, {"text", "Small text"}};
            int[][] icons = {LineIcon.PICTURE, LineIcon.PLUS, LineIcon.WIFI, LineIcon.SETTINGS, LineIcon.SETTINGS};
            for (int i = 0; i < items.length; i++) {
                keys.add(SET + items[i][0]);
                labels.add(Strings.t(items[i][1]));
                lineIcons.add(icons[i]);
            }
        }
        int n = keys.size();
        int content = n * w + Math.max(0, n - 1) * g;
        rowLeft = Math.max(px(48), (px(SHELF_W) - content) / 2);
        for (int i = 0; i < n; i++) {
            String key = keys.get(i);
            Drawable banner = null, icon = null;
            if (mode == 0) {
                rowApps.add(key);
                Intent li = launchIntent(pm, key);
                try {
                    banner = pm.getActivityBanner(li);
                    if (banner == null) banner = pm.getApplicationBanner(key);
                } catch (Exception ignored) {}
                // Apps without a TV banner get a plain glass tile with their normal icon.
                if (banner == null) try { icon = pm.getApplicationIcon(key); } catch (Exception ignored) {}
            }
            names.put(key, labels.get(i));
            addTile(appsRow, key, labels.get(i), banner, lineIcons.get(i), icon, gridX(i), gridY(i), w, h);
        }
        appsRow.setMinimumWidth(rowLeft * 2 + content);

        // The tab bar, centred at the top.
        int barLeft = (screenW - px(552)) / 2 + px(8);
        for (int i = 0; i < TAB_NAMES.length; i++) addTab(i, barLeft + i * px(180));

        // Round icons at the top right, in a glass pill: any settings shortcuts, then the network icon.
        // Added last, so the first app keeps first focus; Up from the apps reaches them.
        List<Integer> quick = quickList();
        int ib = px(ICON_BUTTON), ig = px(ICON_GAP);
        int count = quick.size() + 1;
        int capW = count * ib + (count - 1) * ig + px(24);
        int capX = screenW - m - capW;
        FrameLayout.LayoutParams cl = (FrameLayout.LayoutParams) capsule.getLayoutParams();
        cl.leftMargin = capX;
        cl.width = capW;
        capsule.setLayoutParams(cl);
        int qx = capX + px(12);
        for (int q : quick) {
            addIcon(QUICK + QUICK_NAMES[q], Strings.t(QUICK_LABELS[q]), new LineIcon(QUICK_ICONS[q]), qx);
            qx += ib + ig;
        }
        netIcon = addIcon(NETWORK, Strings.t("Network"), null, qx);
        netState = -1;
        refreshNetwork();

        // The first time the page is built, everything springs up into place, one after another
        // (about a second and a half in all). Rebuilds after that, like an app being installed, don't
        // replay it. While it plays the row holds off dimming and opening up.
        if (!entered) {
            entered = true;
            intro = true;
            int rise = px(70), k = 0;
            for (View t : tiles) {
                View wrap = (View) t.getParent();
                wrap.setAlpha(0f);
                wrap.setTranslationY(rise);
                wrap.setScaleX(0.88f);
                wrap.setScaleY(0.88f);
                wrap.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                        .setStartDelay(150 + Math.min(k++, 14) * 55L).setDuration(560).setInterpolator(POP)
                        .withLayer().start();
            }
            root.postDelayed(() -> {
                intro = false;
                syncFocus();
            }, 1600);
        }

        root.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        // An app that was uninstalled hands focus to the one now in its place, as Remove does.
        if (wantAt >= 0 && !rowApps.contains(want))
            want = rowApps.isEmpty() ? TAB + mode : rowApps.get(Math.min(wantAt, rowApps.size() - 1));
        View restore = null;
        for (View t : tiles) if (t.getTag().equals(want)) restore = t;
        if (restore != null) restore.requestFocus();
    }

    // One of the three tabs in the bar at the top: the open one is solid white.
    private void addTab(final int idx, int x) {
        final String key = TAB + idx;
        names.put(key, TAB_NAMES[idx]);
        final boolean current = idx == mode;
        final TextView t = text(px(25), current ? 0xFF121214 : Color.WHITE, semibold);
        t.setText(Strings.t(TAB_NAMES[idx]));
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(28));
        bg.setColor(current ? 0xFFFFFFFF : Color.TRANSPARENT);
        t.setBackground(bg);
        t.setFocusable(true);
        t.setTag(key);
        t.setOnFocusChangeListener((v, has) -> {
            if (has) focusedKey = key;
            if (!current) bg.setColor(has ? 0x4DFFFFFF : Color.TRANSPARENT);
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setStartDelay(0)
                    .setDuration(has ? 240 : 160).setInterpolator(has ? POP : EASE).start();
        });
        t.setOnClickListener(v -> open(key));
        FrameLayout wrap = new FrameLayout(this);
        wrap.setClipChildren(false);
        wrap.addView(t, new FrameLayout.LayoutParams(px(176), px(56)));
        root.addView(wrap, at(x, px(72), px(176), px(56)));
        tiles.add(t);
    }

    private void addTile(ViewGroup parent, final String key, String label, Drawable banner, int[] lineIcon, Drawable appIcon,
                         int x, int y, final int w, int h) {
        FrameLayout card = new FrameLayout(this);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(RADIUS));
        Drawable art = null; // the picture as drawn, for the colour of the glow behind it
        if (banner != null) {
            bg.setColor(0xFF1E1A1C);
            ImageView iv = new ImageView(this);
            art = fit(banner, w, h);
            iv.setImageDrawable(art);
            card.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            // Quiet "glass" tile: icon above label.
            bg.setColor(0x13FFFFFF);
            bg.setStroke(Math.max(1, px(2)), 0x17FFFFFF);
            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setGravity(Gravity.CENTER);
            ImageView iv = new ImageView(this);
            int size = lineIcon != null ? px(44) : px(60);
            if (lineIcon != null) iv.setImageDrawable(new LineIcon(lineIcon));
            else if (appIcon != null) {
                art = fit(appIcon, size, size);
                iv.setImageDrawable(art);
            }
            inner.addView(iv, new LinearLayout.LayoutParams(size, size));
            TextView t = text(px(22), 0xE0FFFFFF, regular);
            t.setText(label);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setGravity(Gravity.CENTER);
            t.setPadding(px(16), 0, px(16), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.topMargin = px(10);
            inner.addView(t, lp);
            card.addView(inner, new FrameLayout.LayoutParams(-1, -1));
        }
        card.setBackground(bg);
        card.setClipToOutline(true);

        int tint = tintOf(art);
        if (tint != 0) {
            tints.put(key, tint);
            // From Android 9 the shadow under a lifted tile takes the app's colour too.
            if (android.os.Build.VERSION.SDK_INT >= 28) card.setOutlineSpotShadowColor(tint | 0xFF000000);
        }

        // A soft band of light that crosses the tile once when it gets focus, and a thin light ring.
        // Both sit on top of the picture, inside the tile's rounded clip.
        final int sw = w / 3;
        final View shine = new View(this);
        shine.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {0x00FFFFFF, 0x44FFFFFF, 0x00FFFFFF}));
        shine.setAlpha(0f);
        card.addView(shine, new FrameLayout.LayoutParams(sw, -1));
        final View ring = new View(this);
        GradientDrawable ringShape = new GradientDrawable();
        ringShape.setCornerRadius(px(RADIUS));
        ringShape.setStroke(Math.max(2, px(3)), 0xF2FFFFFF);
        ring.setBackground(ringShape);
        ring.setAlpha(0f);
        card.addView(ring, new FrameLayout.LayoutParams(-1, -1));
        card.setFocusable(true);
        card.setTag(key);

        final boolean glass = banner == null;
        card.setOnFocusChangeListener((v, has) -> {
            if (has) focusedKey = key;
            if (glass) bg.setColor(has ? 0x38FFFFFF : 0x13FFFFFF);
            v.animate().scaleX(has ? POP_SCALE : 1f).scaleY(has ? POP_SCALE : 1f).translationZ(has ? px(26) : 0)
                    .setStartDelay(0).setDuration(has ? 300 : 200).setInterpolator(has ? POP : EASE).withLayer().start();
            ring.animate().alpha(has ? 1f : 0f).setStartDelay(0).setDuration(has ? 200 : 140).start();
            if (has && !intro) sweep(shine, sw, w);
        });
        final boolean editable = rowApps.contains(key);
        card.setOnClickListener(v -> open(key));
        if (editable) card.setOnLongClickListener(v -> { showEditMenu(key); return true; });
        card.setOnKeyListener((v, code, ev) -> {
            boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                    || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
            boolean down = ev.getAction() == KeyEvent.ACTION_DOWN;
            if (key.equals(movingKey)) return moveKey(key, code, ok, down);
            if (editable && code == KeyEvent.KEYCODE_MENU) {
                if (!down) showEditMenu(key);
                return true;
            }
            if (!ok) return false;
            if (down) {
                if (ev.getRepeatCount() == 0) {
                    longPressed = false;
                    okDownKey = key;
                } else if (editable && !longPressed && (ev.isLongPress()
                        || ev.getEventTime() - ev.getDownTime() >= ViewConfiguration.getLongPressTimeout())) {
                    longPressed = true;
                    showEditMenu(key);
                }
            } else {
                // Only the key-up of an OK that went down on this tile opens the app, and not a cancelled
                // one (focus left while OK was held). View does the same for a click with isPressed().
                boolean pressed = key.equals(okDownKey) && !ev.isCanceled();
                okDownKey = null;
                if (pressed && !longPressed) open(key);
            }
            return true;
        });

        FrameLayout wrap = new FrameLayout(this);
        wrap.setClipChildren(false);
        wrap.addView(card, new FrameLayout.LayoutParams(w, h));
        parent.addView(wrap, at(x, y, w, -2));
        tiles.add(card);
    }

    // A pill in the dock: a small icon and a name. Lights up softly when focused.
    private void addPill(final String key, String label, int[] lineIcon) {
        names.put(key, label);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(px(26), 0, px(30), 0);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(32));
        bg.setColor(Color.TRANSPARENT);
        card.setBackground(bg);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(new LineIcon(lineIcon));
        card.addView(iv, new LinearLayout.LayoutParams(px(34), px(34)));
        TextView t = text(px(24), 0xE6FFFFFF, semibold);
        t.setText(label);
        t.setSingleLine(true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.leftMargin = px(12);
        card.addView(t, tp);
        card.setFocusable(true);
        card.setTag(key);
        card.setOnFocusChangeListener((v, has) -> {
            if (has) focusedKey = key;
            bg.setColor(has ? 0x40FFFFFF : Color.TRANSPARENT);
            v.animate().scaleX(has ? 1.07f : 1f).scaleY(has ? 1.07f : 1f).setStartDelay(0)
                    .setDuration(has ? 240 : 160).setInterpolator(has ? POP : EASE).start();
        });
        card.setOnClickListener(v -> open(key));

        FrameLayout wrap = new FrameLayout(this);
        wrap.setClipChildren(false);
        wrap.addView(card, new FrameLayout.LayoutParams(-2, px(64)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, px(64));
        lp.leftMargin = px(4);
        lp.rightMargin = px(4);
        dock.addView(wrap, lp);
        tiles.add(card);
    }

    // A round glass button, top right: it brightens and pops a little when focused.
    private ImageView addIcon(final String key, String label, Drawable icon, int x) {
        names.put(key, label);
        int b = px(ICON_BUTTON), size = px(ICON_SIZE);
        FrameLayout card = new FrameLayout(this);
        final GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x00FFFFFF);
        card.setBackground(bg);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(icon);
        card.addView(iv, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        card.setFocusable(true);
        card.setTag(key);
        card.setContentDescription(label);
        card.setOnFocusChangeListener((v, has) -> {
            if (has) focusedKey = key;
            bg.setColor(has ? 0x59FFFFFF : 0x00FFFFFF);
            v.animate().scaleX(has ? 1.12f : 1f).scaleY(has ? 1.12f : 1f).setStartDelay(0)
                    .setDuration(has ? 240 : 160).setInterpolator(has ? POP : EASE).start();
        });
        card.setOnClickListener(v -> open(key));
        FrameLayout wrap = new FrameLayout(this);
        wrap.setClipChildren(false);
        wrap.addView(card, new FrameLayout.LayoutParams(b, b));
        root.addView(wrap, at(x, px(ICON_TOP), b, b));
        tiles.add(card);
        return iv;
    }

    // A soft band of light crosses a tile once as it gets focus.
    private void sweep(final View shine, int sw, int w) {
        shine.animate().cancel();
        shine.setTranslationX(-sw);
        shine.setAlpha(1f);
        shine.animate().translationX(w).setStartDelay(80).setDuration(560).setInterpolator(EASE)
                .withEndAction(() -> shine.setAlpha(0f)).start();
    }

    // A bright, see-through version of the colour an app's picture is mostly made of, for the glow
    // behind its tile. 0 when the picture is grey, so the wallpaper's own glow is used instead.
    private static int tintOf(Drawable d) {
        if (!(d instanceof BitmapDrawable)) return 0;
        try {
            Bitmap b = ((BitmapDrawable) d).getBitmap();
            int bw = b.getWidth(), bh = b.getHeight();
            int sx = Math.max(1, bw / 16), sy = Math.max(1, bh / 9);
            float[] hsv = new float[3];
            double r = 0, g = 0, bl = 0, total = 0;
            for (int yy = 0; yy < bh; yy += sy) {
                for (int xx = 0; xx < bw; xx += sx) {
                    int p = b.getPixel(xx, yy);
                    if (Color.alpha(p) < 160) continue;
                    Color.colorToHSV(p, hsv);
                    double wt = hsv[1] * hsv[2];
                    wt = wt * wt; // vivid pixels count most
                    r += Color.red(p) * wt;
                    g += Color.green(p) * wt;
                    bl += Color.blue(p) * wt;
                    total += wt;
                }
            }
            if (total < 1.0) return 0;
            Color.RGBToHSV((int) (r / total), (int) (g / total), (int) (bl / total), hsv);
            hsv[1] = Math.max(hsv[1], 0.55f);
            hsv[2] = 1f;
            return Color.HSVToColor(0x5C, hsv);
        } catch (Throwable t) {
            return 0;
        }
    }

    // The glow used when an app has no colour of its own: the wallpaper's own light, a little softer.
    private int defaultTint() {
        return (WALLPAPERS[wallpaper()][3] & 0x00FFFFFF) | 0x50000000;
    }

    private void paintGlow(int c, float x) {
        tintColor = c;
        tintX = x;
        if (tintGlow == null) return;
        tintGlow.setColors(new int[] {c, c & 0x00FFFFFF});
        tintGlow.setGradientCenter(x, 0.66f);
    }

    // Slides the glow behind the apps to a new colour and place.
    private void glowTo(final int color, final float x, long ms) {
        if (tintGlow == null) return;
        if (tintAnim != null) tintAnim.cancel();
        final int from = tintColor == 0 ? (color & 0x00FFFFFF) : tintColor;
        final float x0 = tintX;
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(ms);
        a.setInterpolator(EASE);
        a.addUpdateListener(an -> {
            float t = (Float) an.getAnimatedValue();
            paintGlow((Integer) argb.evaluate(t, from, color), x0 + (x - x0) * t);
        });
        tintAnim = a;
        a.start();
    }

    // The small print's surprise: the glow sweeps the whole screen through the colours of the
    // northern lights while the tiles hop one after another. Then everything settles back.
    private void surprise() {
        if (tintAnim != null) tintAnim.cancel();
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(2800);
        a.setInterpolator(new android.view.animation.LinearInterpolator());
        a.addUpdateListener(an -> {
            float t = (Float) an.getAnimatedValue();
            float[] hsv = {130f + 200f * t, 0.78f, 1f};
            paintGlow(Color.HSVToColor(0x70, hsv), 0.06f + 0.88f * t);
        });
        final boolean[] cancelled = {false};
        a.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationCancel(Animator an) { cancelled[0] = true; }
            @Override public void onAnimationEnd(Animator an) { if (!cancelled[0]) syncFocus(); }
        });
        tintAnim = a;
        a.start();
        wave();
    }

    // Every app tile hops up and settles back, left to right.
    private void wave() {
        int k = 0;
        for (String key : rowApps) {
            View card = tile(key);
            if (card == null) continue;
            final View wrap = (View) card.getParent();
            wrap.animate().translationY(-px(34)).setStartDelay(k++ * 70L).setDuration(200).setInterpolator(EASE)
                    .withEndAction(() -> wrap.animate().translationY(0f).setStartDelay(0).setDuration(420)
                            .setInterpolator(POP).start()).start();
        }
    }

    // The row opens up around the focused app: the ones before it slide left, the ones after it slide
    // right, and all of them dim a little. With focus anywhere else the row rests.
    private void rowState(int focus) {
        if (intro) return;
        for (int j = 0; j < rowApps.size(); j++) {
            View card = tile(rowApps.get(j));
            if (card == null) continue;
            View wrap = (View) card.getParent();
            boolean rest = focus < 0 || j == focus;
            float push = rest ? 0f : (j < focus ? -px(PUSH) : px(PUSH));
            wrap.animate().translationX(push).alpha(rest ? 1f : 0.62f)
                    .setStartDelay(0).setDuration(280).setInterpolator(EASE).start();
        }
    }

    private void syncFocus() {
        onFocusMoved(getCurrentFocus());
    }

    // Everything that follows focus: the name under the row and how the row opens up.
    private void onFocusMoved(View f) {
        if (f == null || nameText == null || appsScroll == null) return;
        try {
            Object tag = f.getTag();
            String key = tag instanceof String ? (String) tag : null;
            int idx = key == null ? -1 : rowApps.indexOf(key);
            rowState(idx);
            showLabel(idx >= 0 ? names.get(key) : "");
        } catch (Exception ignored) {}
    }

    private void showLabel(String name) {
        if (name == null) name = "";
        if (name.contentEquals(nameText.getText())) return;
        nameText.setText(name);
        nameText.setAlpha(0f);
        nameText.setTranslationY(px(10));
        nameText.animate().alpha(1f).translationY(0f).setStartDelay(0).setDuration(240).setInterpolator(EASE).start();
    }

    private void showHeader(String section, String name, String count) {
        sectionLabel.setText(section);
        countText.setText(count);
        if (name == null) name = "";
        if (!name.contentEquals(nameText.getText())) {
            nameText.setText(name);
            nameText.setAlpha(0f);
            nameText.setTranslationY(px(16));
            nameText.animate().alpha(1f).translationY(0f).setStartDelay(0).setDuration(240).setInterpolator(EASE).start();
        }
    }

    // The small print under the dock. Change it with --es credit "text", or hold OK on it.
    private String creditText() {
        return getSharedPreferences(PREFS, 0).getString("credit", "Built by Aryan");
    }

    private void editCredit() {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setText(creditText());
        et.setSingleLine(true);
        et.setSelectAllOnFocus(true);
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Small text under the dock")
                .setView(et)
                .setPositiveButton("Save", (d, which) -> {
                    String s = et.getText().toString().trim();
                    SharedPreferences.Editor e = getSharedPreferences(PREFS, 0).edit();
                    if (s.isEmpty()) e.remove("credit");
                    else e.putString("credit", s);
                    e.apply();
                    credit.setText(creditText());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void open(String key) {
        Intent i;
        if (key.startsWith(TAB)) {
            int next = Integer.parseInt(key.substring(TAB.length()));
            if (next != mode) {
                mode = next;
                buildTiles();
            }
            return;
        } else if (key.startsWith(SET)) {
            openSetting(key.substring(SET.length()));
            return;
        } else if (ADD.equals(key)) {
            showAddMenu(-1);
            return;
        } else if (isInput(key)) {
            openInput(key);
            return;
        } else if (NETWORK.equals(key)) {
            openNetworkSettings();
            return;
        } else if (key.startsWith(QUICK)) {
            openQuick(key.substring(QUICK.length()));
            return;
        } else {
            i = launchIntent(getPackageManager(), key);
            if (i == null) return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception ignored) {}
    }

    // The tiles on the Settings tab.
    private void openSetting(String name) {
        if (name.equals("wallpaper")) showWallpapers();
        else if (name.equals("add")) {
            // Adding saves the row as shown, so show the apps first.
            mode = 0;
            buildTiles();
            showAddMenu(-1);
        } else if (name.equals("city")) showCities();
        else if (name.equals("text")) editCredit();
        else try { startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception ignored) {}
    }

    // Live TV apps an HDMI tile can open, in order. Droidlogic (Amlogic) TVs ship Live TV under their
    // own package, with TvActivity as its entry point.
    private static final String[][] LIVE_TV = {{"com.android.tv", "com.android.tv.MainActivity"},
            {"com.droidlogic.android.tv", "com.android.tv.TvActivity"}};
    private static final String INPUT_PLAYER = "com.google.android.tv.inputplayer";

    // Opens an HDMI input: the stock Live TV app first, then Droidlogic's, then whatever the TV picks.
    // Each one is tried only when the one before it can't start, so a TV where the stock app already
    // worked takes exactly the same path as before. The one exception: when the TV itself sends HDMI to
    // Google TV's input player, that goes first, because some Google TVs ship com.android.tv as a stub
    // that starts fine but only says "Hello World!" (issue #5).
    private void openInput(String id) {
        Intent view = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id));
        List<Intent> tries = new ArrayList<>();
        ResolveInfo def = getPackageManager().resolveActivity(view, PackageManager.MATCH_DEFAULT_ONLY);
        if (def != null && def.activityInfo != null && INPUT_PLAYER.equals(def.activityInfo.packageName))
            tries.add(new Intent(view).setClassName(INPUT_PLAYER, def.activityInfo.name));
        for (String[] live : LIVE_TV) {
            Intent t = new Intent(view).setClassName(live[0], live[1]);
            if (t.resolveActivity(getPackageManager()) != null) tries.add(t);
        }
        tries.add(view);
        for (Intent t : tries) {
            t.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(t);
                return;
            } catch (Exception ignored) {}
        }
    }

    // The shortcuts picked with --es quick, in that order, as indexes into QUICK_NAMES. A shortcut
    // this TV has no screen for is left out, so it never shows a tile that does nothing.
    private List<Integer> quickList() {
        List<Integer> out = new ArrayList<>();
        String saved = getSharedPreferences(PREFS, 0).getString("quick", null);
        if (saved == null) saved = "bluetooth,sound"; // unless picked otherwise
        for (String name : saved.split("[,\\s]+")) {
            int q = Arrays.asList(QUICK_NAMES).indexOf(name.toLowerCase(java.util.Locale.ROOT));
            if (q < 0 || out.contains(q) || quickIntents(QUICK_NAMES[q]).isEmpty()) continue;
            out.add(q);
        }
        return out;
    }

    // OFFLINE, 1 to 3 for Wi-Fi with that many bars, or ETHERNET (any other link, which on a TV is a cable).
    private int networkState() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return OFFLINE;
        try {
            int rssi = Integer.MIN_VALUE;
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                NetworkCapabilities c = cm.getNetworkCapabilities(cm.getActiveNetwork());
                if (c == null) return OFFLINE;
                if (!c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return ETHERNET;
                if (android.os.Build.VERSION.SDK_INT >= 29) rssi = c.getSignalStrength();
            } else {
                android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
                if (ni == null || !ni.isConnected()) return OFFLINE;
                if (ni.getType() != ConnectivityManager.TYPE_WIFI) return ETHERNET;
            }
            if (rssi == Integer.MIN_VALUE) { // Android 9 and older: ask the Wi-Fi service
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null && wm.getConnectionInfo() != null) rssi = wm.getConnectionInfo().getRssi();
            }
            return 1 + WifiManager.calculateSignalLevel(rssi, 3);
        } catch (Exception e) {
            return OFFLINE;
        }
    }

    private void refreshNetwork() {
        if (netIcon == null) return;
        int state = networkState();
        if (state == netState) return;
        netState = state;
        if (state == ETHERNET) {
            netIcon.setImageDrawable(new LineIcon(LineIcon.ETHERNET));
        } else {
            // The whole Wi-Fi fan dim, with the bars it has bright on top. Offline is a slash instead.
            LineIcon fan = new LineIcon(LineIcon.WIFI);
            fan.setAlpha(0x47);
            int[] top = state == OFFLINE ? LineIcon.SLASH : state == 1 ? LineIcon.WIFI_1
                    : state == 2 ? LineIcon.WIFI_2 : LineIcon.WIFI;
            netIcon.setImageDrawable(new LayerDrawable(new Drawable[] {fan, new LineIcon(top)}));
        }
        ((View) netIcon.getParent()).setContentDescription(
                state == OFFLINE ? Strings.t("Offline") : state == ETHERNET ? "Ethernet" : "Wi-Fi");
    }

    // Wi-Fi settings (on Android TV that page lists Ethernet too), else the TV's network page, else Settings.
    private void openNetworkSettings() {
        for (String a : new String[] {Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS,
                Settings.ACTION_SETTINGS}) {
            try {
                startActivity(new Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) {}
        }
    }

    // Screens a shortcut can open, best first, keeping only those this TV has.
    private List<Intent> quickIntents(String name) {
        List<Intent> all = new ArrayList<>();
        String tclType = name.equals("sound") ? "SoundPreset" : name.equals("picture") ? "picture" : null;
        if (tclType != null)
            all.add(new Intent(TCL_SHOW_WINDOW).setClassName("com.tcl.settings", "com.tcl.settings.ShowWindowService")
                    .putExtra("Type", tclType));
        switch (name) {
            case "bluetooth":
                all.add(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                all.add(new Intent("com.google.android.intent.action.CONNECT_INPUT")); // Android TV's pairing screen
                break;
            case "sound": all.add(new Intent(Settings.ACTION_SOUND_SETTINGS)); break;
            case "picture": all.add(new Intent(Settings.ACTION_DISPLAY_SETTINGS)); break;
        }
        PackageManager pm = getPackageManager();
        List<Intent> out = new ArrayList<>();
        for (Intent i : all) {
            boolean service = TCL_SHOW_WINDOW.equals(i.getAction());
            if (service ? pm.resolveService(i, 0) != null : i.resolveActivity(pm) != null) out.add(i);
        }
        return out;
    }

    // Opens a shortcut's screen: the first one that starts wins.
    private void openQuick(String name) {
        for (Intent i : quickIntents(name)) {
            try {
                if (TCL_SHOW_WINDOW.equals(i.getAction())) startService(i);
                else startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) {}
        }
    }

    interface Pick { void at(int which); }
    interface Icon { Drawable at(int which) throws Exception; }

    private void panel(String title, List<String> labels, Pick pick) {
        panel(title, labels, null, 0, -1, null, pick);
    }

    // A side panel in the home screen's own look: a title, then one row per choice, with the same
    // glass focus as the tiles. Back closes it. icon (or null) puts an icon of that size by each name.
    // With preview, the list starts on selected and moving through it calls preview; Back calls it
    // with selected again, to put things back.
    private void panel(String title, final List<String> labels, final Icon icon, final int size,
                       final int selected, final Pick preview, final Pick pick) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(px(48), px(92), px(48), px(48));

        TextView head = text(px(40), Color.WHITE, semibold);
        head.setText(title);
        head.setSingleLine(true);
        head.setEllipsize(android.text.TextUtils.TruncateAt.END);
        head.setPadding(px(24), 0, px(24), 0);
        box.addView(head);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        GradientDrawable focus = new GradientDrawable();
        focus.setCornerRadius(px(RADIUS));
        focus.setColor(0x38FFFFFF);
        list.setSelector(focus);
        // Icons load as rows appear, drawn small to save RAM.
        final Drawable[] icons = new Drawable[labels.size()];
        list.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return labels.size(); }
            @Override public Object getItem(int pos) { return labels.get(pos); }
            @Override public long getItemId(int pos) { return pos; }
            @Override public View getView(int pos, View convert, ViewGroup parent) {
                TextView t = convert != null ? (TextView) convert : text(px(30), 0xE6FFFFFF, regular);
                t.setText(labels.get(pos));
                t.setSingleLine(true);
                t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                t.setGravity(Gravity.CENTER_VERTICAL);
                t.setMinHeight(px(88));
                t.setPadding(px(24), 0, px(24), 0);
                if (icon != null && icons[pos] == null) {
                    try { icons[pos] = icon.at(pos); }
                    catch (Exception e) { icons[pos] = new ColorDrawable(Color.TRANSPARENT); }
                    icons[pos].setBounds(0, 0, size, size);
                }
                t.setCompoundDrawablesRelative(icons[pos], null, null, null);
                t.setCompoundDrawablePadding(px(24));
                return t;
            }
        });
        list.setOnItemClickListener((parent, v, pos, id) -> {
            d.dismiss();
            pick.at(pos);
        });
        if (preview != null) {
            list.setSelection(selected);
            list.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                    preview.at(pos);
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
            });
            d.setOnCancelListener(x -> preview.at(selected));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lp.topMargin = px(36);
        box.addView(list, lp);

        d.setContentView(box);
        Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(preview != null ? 0xFF161113 : 0xF7161113));
        w.setLayout(px(640), -1);
        w.setGravity(Gravity.END);
        w.setDimAmount(preview != null ? 0f : 0.5f); // a preview needs the home screen undimmed
        d.show();
    }

    // A small menu that drops down under a tile, in the same glass as the tiles. Opens above the
    // tile when there's no room below. Back closes it.
    private void dropdown(View anchor, String[] items, final Pick pick) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = px(10), rowH = px(64);
        box.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(RADIUS));
        bg.setColor(0xFF231D1F);
        bg.setStroke(Math.max(1, px(2)), 0x17FFFFFF);
        box.setBackground(bg);
        float widest = 0; // some languages need more room than the tile is wide
        for (int i = 0; i < items.length; i++) {
            final int which = i;
            final TextView t = text(px(26), 0xD9FFFFFF, regular);
            t.setText(Strings.t(items[i]));
            t.setSingleLine(true);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding(px(22), 0, px(22), 0);
            widest = Math.max(widest, t.getPaint().measureText(t.getText().toString()) + 2 * px(22) + 2 * pad);
            final GradientDrawable focus = new GradientDrawable();
            focus.setCornerRadius(px(RADIUS - pad));
            focus.setColor(Color.TRANSPARENT);
            t.setBackground(focus);
            t.setFocusable(true);
            t.setOnFocusChangeListener((v, has) -> {
                focus.setColor(has ? 0x38FFFFFF : Color.TRANSPARENT);
                t.setTextColor(has ? Color.WHITE : 0xD9FFFFFF);
            });
            t.setOnClickListener(v -> {
                d.dismiss();
                pick.at(which);
            });
            box.addView(t, new LinearLayout.LayoutParams(-1, rowH));
        }
        d.setContentView(box);

        // Line it up with the tile as drawn (focused tiles are scaled up), below its name.
        int[] at = new int[2];
        anchor.getLocationOnScreen(at);
        int tileBottom = at[1] + Math.round(anchor.getHeight() * anchor.getScaleY());
        int w = Math.max(Math.max(Math.round(anchor.getWidth() * anchor.getScaleX()), px(320)), (int) Math.ceil(widest));
        int h = items.length * rowH + 2 * pad;
        int y = tileBottom + px(64);
        if (y + h > getResources().getDisplayMetrics().heightPixels - px(24)) y = at[1] - h - px(16);
        Window win = d.getWindow();
        win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        win.setGravity(Gravity.TOP | Gravity.START);
        android.view.WindowManager.LayoutParams lp = win.getAttributes();
        lp.x = at[0];
        lp.y = y;
        lp.width = w;
        lp.height = h;
        win.setAttributes(lp);
        d.show();
    }

    // Hold OK (or Menu) on a top-row app: move it, swap it for another, or take it off the home
    // screen. Removing only takes the tile away: the app stays installed and can be added back.
    // Apps the user installed can also be uninstalled; Android asks to confirm, and the tile goes
    // once the app is gone. Apps that came with the TV can't be, so they get App info instead,
    // where Android offers to turn them off.
    private void showEditMenu(final String key) {
        final int index = rowApps.indexOf(key);
        if (index < 0) return;
        final boolean user = userInstalled(key);
        dropdown(tile(key), user ? EDIT_ITEMS : EDIT_ITEMS_SYSTEM, which -> {
            if (which == 0) startMove(key);
            else if (which == 1) showAddMenu(index);
            else if (which == 3) {
                Uri pkg = Uri.parse("package:" + key);
                try { startActivity(user ? new Intent(Intent.ACTION_DELETE, pkg)
                        : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)); }
                catch (Exception ignored) {}
            } else {
                rowApps.remove(index);
                saveRow();
                focusedKey = rowApps.isEmpty() ? ADD : rowApps.get(Math.min(index, rowApps.size() - 1));
                buildTiles();
            }
        });
    }

    // Every launchable app that isn't on the top row yet, by name. The pick is added at the end,
    // or takes the place of the app at replaceIndex (-1 to add).
    private void showAddMenu(final int replaceIndex) {
        final PackageManager pm = getPackageManager();
        TreeMap<String, String> byLabel = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String p = ri.activityInfo.packageName;
                if (rowApps.contains(p) || byLabel.containsValue(p) || p.equals(getPackageName())
                        || (NOT_APPS.contains(p) && !p.equals(PLAY_STORE))) continue;
                String label = labelOf(p);
                if (!byLabel.containsKey(label)) byLabel.put(label, p);
            }
        }
        if (byLabel.isEmpty()) {
            panel(Strings.t("Every app is already here"), Collections.singletonList("OK"), which -> {});
            return;
        }
        final List<String> pkgs = new ArrayList<>(byLabel.values());
        String title = replaceIndex < 0 ? Strings.t("Add an app")
                : String.format(Strings.t("Replace %s"), labelOf(rowApps.get(replaceIndex)));
        panel(title, new ArrayList<>(byLabel.keySet()), pos -> fit(pm.getApplicationIcon(pkgs.get(pos)), px(48), px(48)),
                px(48), -1, null, which -> {
            if (replaceIndex < 0) rowApps.add(pkgs.get(which));
            else rowApps.set(replaceIndex, pkgs.get(which));
            saveRow();
            focusedKey = pkgs.get(which);
            buildTiles();
        });
    }

    private String labelOf(String pkg) {
        PackageManager pm = getPackageManager();
        try { return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); }
        catch (Exception e) { return pkg; }
    }

    // Saves the top row as the app list, so it no longer follows apps.txt.
    private void saveRow() {
        getSharedPreferences(PREFS, 0).edit().putString("apps", android.text.TextUtils.join(",", rowApps)).apply();
    }

    // Where app number i sits in the sideways row.
    private int gridX(int i) {
        return rowLeft + i * (tileW + px(TILE_GAP));
    }

    private int gridY(int i) {
        return px(TILE_IN);
    }

    private View tile(String key) {
        for (View t : tiles) if (t.getTag().equals(key)) return t;
        return null;
    }

    // Move mode: the tile gets an outline, the arrows move it through the grid, OK or Back finishes.
    private void startMove(String key) {
        View card = tile(key);
        if (card == null) return;
        movingKey = key;
        GradientDrawable outline = new GradientDrawable();
        outline.setCornerRadius(px(RADIUS));
        outline.setStroke(px(4), Color.WHITE);
        ((FrameLayout) card).setForeground(outline);
        moveHint.setVisibility(View.VISIBLE);
        card.requestFocus();
    }

    private boolean moveKey(String key, int code, boolean ok, boolean down) {
        int step = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1
                : 0; // one row now: up and down stay put while moving
        if (step != 0) {
            int from = rowApps.indexOf(key), to = from + step;
            if (down && to >= 0 && to < rowApps.size()) {
                // Slide the tiles into their new places: no rebuild, so it stays quick on slow TVs.
                rowApps.add(to, rowApps.remove(from));
                for (int i = Math.min(from, to); i <= Math.max(from, to); i++) {
                    View wrap = (View) tile(rowApps.get(i)).getParent();
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) wrap.getLayoutParams();
                    lp.leftMargin = gridX(i);
                    lp.topMargin = gridY(i);
                    wrap.setLayoutParams(lp);
                }
                // Scroll along with it once it has moved.
                final View card = tile(key);
                card.post(() -> card.requestRectangleOnScreen(
                        new android.graphics.Rect(0, 0, card.getWidth(), card.getHeight())));
            }
            return true;
        }
        if (ok || code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU) {
            if (!down) {
                movingKey = null;
                longPressed = false;
                ((FrameLayout) tile(key)).setForeground(null);
                moveHint.setVisibility(View.GONE);
                saveRow();
                refreshApps();
                syncFocus();
            }
            return true;
        }
        return true; // up/down and everything else stay put while moving
    }

    private static boolean isInput(String key) {
        return key.indexOf('/') > 0;
    }

    // The TV's own HDMI inputs (not devices behind them), in order, skipping hidden ones.
    private List<TvInputInfo> hdmiInputs() {
        List<TvInputInfo> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        for (TvInputInfo in : tim.getTvInputList()) {
            if (in.getType() == TvInputInfo.TYPE_HDMI && in.getParentId() == null && !in.isHidden(this)) out.add(in);
        }
        Collections.sort(out, (a, b) -> a.getId().compareTo(b.getId()));
        return out.size() > MAX_INPUTS ? out.subList(0, MAX_INPUTS) : out;
    }

    // The name set in Settings > Inputs (like "Bedroom"), else the TV's own name (like "HDMI 1").
    private String inputLabel(TvInputInfo in) {
        CharSequence custom = in.loadCustomLabel(this);
        if (custom != null && custom.length() > 0) return withoutDeviceType(custom.toString());
        CharSequence label = in.loadLabel(this);
        return label != null && label.length() > 0 ? withoutDeviceType(label.toString()) : "HDMI";
    }

    // Philips stores a name as "name&DEVICE_TYPE" (like "Computer&COMPUTER"), so show the name only.
    // Only on Philips TVs: elsewhere a name like "AUDIO&VIDEO" is the name itself.
    private static String withoutDeviceType(String label) {
        if (!"Philips".equalsIgnoreCase(android.os.Build.MANUFACTURER)) return label;
        int amp = label.lastIndexOf('&');
        return amp > 0 && label.substring(amp + 1).matches("[A-Z][A-Z0-9_]{2,}") ? label.substring(0, amp).trim() : label;
    }

    private boolean userInstalled(String pkg) {
        try { return (getPackageManager().getApplicationInfo(pkg, 0).flags & ApplicationInfo.FLAG_SYSTEM) == 0; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }

    private static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    // Xiaomi TVs ship MiLan Pro but don't register it by name; every other TV gets Roboto.
    private static void loadFonts() {
        if (regular != null) return;
        regular = fromFile("/system/fonts/MILanPro_Normal.ttf", Typeface.create("sans-serif", Typeface.NORMAL));
        semibold = fromFile("/system/fonts/MILanPro_SemiBold.ttf", Typeface.create("sans-serif-medium", Typeface.NORMAL));
        light = Typeface.create("sans-serif-light", Typeface.NORMAL);
    }

    private static Typeface fromFile(String path, Typeface fallback) {
        try { return new File(path).exists() ? Typeface.createFromFile(path) : fallback; }
        catch (Exception e) { return fallback; }
    }

    // Draws a banner once into a tile-sized bitmap (center-cropped). Some apps ship huge banners
    // that Android 9 refuses to draw ("Canvas: trying to draw too large bitmap"); this also saves RAM.
    private Drawable fit(Drawable d, int w, int h) {
        try {
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
            if (iw <= 0 || ih <= 0) { iw = w; ih = h; }
            float s = Math.max((float) w / iw, (float) h / ih);
            int dw = Math.round(iw * s), dh = Math.round(ih * s);
            d.setBounds((w - dw) / 2, (h - dh) / 2, (w - dw) / 2 + dw, (h - dh) / 2 + dh);
            d.draw(new Canvas(b));
            return new BitmapDrawable(getResources(), b);
        } catch (Throwable t) {
            return d;
        }
    }

    private int px(int v) {
        return Math.round(v * scale);
    }

    private TextClock clock(String f12, String f24, int px, int color, Typeface tf) {
        TextClock c = new TextClock(this);
        c.setFormat12Hour(f12);
        c.setFormat24Hour(f24);
        c.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        c.setTextColor(color);
        c.setTypeface(tf);
        return c;
    }

    private TextView text(int px, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        t.setTextColor(color);
        if (tf != null) t.setTypeface(tf);
        return t;
    }

    private static FrameLayout.LayoutParams at(int x, int y, int w, int h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.leftMargin = x;
        lp.topMargin = y;
        return lp;
    }

    // ---- Weather: Open-Meteo (free, no account). Fetched at most every 30 minutes, kept for next time.

    // Where the weather is for: a place picked in Settings, else the place found from the TV's internet
    // address (done once), else Oslo.
    private String[] city() {
        SharedPreferences sp = getSharedPreferences(PREFS, 0);
        String s = sp.getString("city", sp.getString("city_auto", "Oslo,59.9139,10.7522"));
        String[] p = s.split("\\s*,\\s*");
        return p.length >= 3 ? p : new String[] {"Oslo", "59.9139", "10.7522"};
    }

    private static final String[][] CITIES = {
        {"Oslo", "59.9139", "10.7522"}, {"Bergen", "60.3913", "5.3221"}, {"Trondheim", "63.4305", "10.3951"},
        {"Stavanger", "58.9700", "5.7331"}, {"Kristiansand", "58.1467", "7.9956"}, {"Troms\u00F8", "69.6492", "18.9553"},
        {"Drammen", "59.7440", "10.2045"}, {"Fredrikstad", "59.2181", "10.9298"}, {"\u00C5lesund", "62.4722", "6.1495"},
        {"Bod\u00F8", "67.2804", "14.4049"}, {"Hamar", "60.7945", "11.0680"}, {"Lillehammer", "61.1153", "10.4662"},
        {"Molde", "62.7375", "7.1591"}, {"Haugesund", "59.4138", "5.2680"}, {"Skien", "59.2094", "9.6090"},
        {"Sandefjord", "59.1312", "10.2167"}, {"T\u00F8nsberg", "59.2676", "10.4076"}, {"Arendal", "58.4615", "8.7725"},
        {"Narvik", "68.4385", "17.4272"}, {"Kristiansund", "63.1115", "7.7280"},
    };

    // The Weather place tile: detect it, pick a city, or type one.
    private void showCities() {
        final List<String> labels = new ArrayList<>();
        labels.add("Detect automatically");
        for (String[] c : CITIES) labels.add(c[0]);
        labels.add("Type a place...");
        panel("Weather place", labels, which -> {
            SharedPreferences.Editor e = getSharedPreferences(PREFS, 0).edit();
            if (which == 0) {
                e.remove("city").remove("city_auto").apply();
                loadWeather(true);
            } else if (which == labels.size() - 1) {
                editCity();
            } else {
                String[] c = CITIES[which - 1];
                e.putString("city", c[0] + "," + c[1] + "," + c[2]).apply();
                loadWeather(true);
            }
        });
    }

    // Finds roughly where the TV is from its internet address, once, and remembers it.
    private boolean detecting;
    private void detectCity() {
        if (detecting) return;
        detecting = true;
        new Thread(() -> {
            try {
                HttpURLConnection con = (HttpURLConnection) new URL("https://get.geojs.io/v1/ip/geo.json").openConnection();
                con.setConnectTimeout(8000);
                con.setReadTimeout(8000);
                InputStream in = con.getInputStream();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                in.close();
                con.disconnect();
                JSONObject o = new JSONObject(out.toString("UTF-8"));
                final String name = o.optString("city", "").isEmpty() ? o.optString("region", "Home") : o.optString("city");
                final String found = name.replace(",", " ") + "," + o.getString("latitude") + "," + o.getString("longitude");
                runOnUiThread(() -> {
                    detecting = false;
                    getSharedPreferences(PREFS, 0).edit().putString("city_auto", found).apply();
                    loadWeather(true);
                });
            } catch (Exception e) {
                detecting = false;
            }
        }).start();
    }

    private void editCity() {
        final android.widget.EditText et = new android.widget.EditText(this);
        String[] c = city();
        et.setText(c[0] + "," + c[1] + "," + c[2]);
        et.setSingleLine(true);
        et.setSelectAllOnFocus(true);
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Weather place: name,latitude,longitude")
                .setView(et)
                .setPositiveButton("Save", (d, which) -> {
                    String s = et.getText().toString().trim();
                    SharedPreferences.Editor e = getSharedPreferences(PREFS, 0).edit();
                    if (s.isEmpty()) e.remove("city");
                    else e.putString("city", s);
                    e.apply();
                    loadWeather(true);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadWeather(boolean force) {
        if (wxTemp == null) return;
        final SharedPreferences sp = getSharedPreferences(PREFS, 0);
        if (!sp.contains("city") && !sp.contains("city_auto")) detectCity();
        final String[] c = city();
        wxCity.setText(c[0].toUpperCase(java.util.Locale.ROOT));
        String cached = sp.getString("wx", null);
        boolean same = c[0].equals(sp.getString("wx_city", ""));
        if (cached != null && same) renderWeather(cached);
        if (!force && cached != null && same
                && System.currentTimeMillis() - sp.getLong("wx_t", 0) < 25 * 60 * 1000L) return;
        final String url = "https://api.open-meteo.com/v1/forecast?latitude=" + c[1] + "&longitude=" + c[2]
                + "&current=temperature_2m,weather_code,is_day&hourly=temperature_2m,weather_code,is_day"
                + "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=2";
        new Thread(() -> {
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(8000);
                con.setReadTimeout(8000);
                InputStream in = con.getInputStream();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                in.close();
                con.disconnect();
                final String json = out.toString("UTF-8");
                runOnUiThread(() -> {
                    sp.edit().putString("wx", json).putString("wx_city", c[0])
                            .putLong("wx_t", System.currentTimeMillis()).apply();
                    renderWeather(json);
                });
            } catch (Exception ignored) {}
        }).start();
    }

    private static boolean rainy(int code) {
        return (code >= 51 && code <= 67) || (code >= 80 && code <= 82) || code >= 95;
    }

    private static int wxKind(int code, boolean day) {
        if (code == 0) return day ? WeatherIcon.SUN : WeatherIcon.MOON;
        if (code <= 2) return day ? WeatherIcon.PARTLY : WeatherIcon.CLOUD;
        if (code == 3 || code == 45 || code == 48) return WeatherIcon.CLOUD;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return WeatherIcon.SNOW;
        if (code >= 95) return WeatherIcon.STORM;
        return WeatherIcon.RAIN;
    }

    private static String wxText(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code == 66 || code == 67) return "Freezing rain";
        if (code >= 61 && code <= 65) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95) return "Thunderstorm";
        return "";
    }

    private void renderWeather(String json) {
        try {
            JSONObject o = new JSONObject(json);
            JSONObject cur = o.getJSONObject("current");
            JSONObject hr = o.getJSONObject("hourly");
            JSONObject day = o.getJSONObject("daily");
            int code = cur.getInt("weather_code");
            boolean isDay = cur.getInt("is_day") == 1;
            long now = Math.round(cur.getDouble("temperature_2m"));
            wxTemp.setText(now + "°");
            wxCond.setText(wxText(code));
            String range = "H " + Math.round(day.getJSONArray("temperature_2m_max").getDouble(0)) + "°   L "
                    + Math.round(day.getJSONArray("temperature_2m_min").getDouble(0)) + "°";
            JSONArray times = hr.getJSONArray("time");
            JSONArray temps = hr.getJSONArray("temperature_2m");
            JSONArray codes = hr.getJSONArray("weather_code");
            JSONArray days = hr.getJSONArray("is_day");
            String hourNow = cur.getString("time").substring(0, 13);
            int start = 0;
            for (int i = 0; i < times.length(); i++) {
                if (times.getString(i).startsWith(hourNow)) { start = i; break; }
            }
            for (int k = 0; k < 6; k++) {
                int i = start + k;
                if (i >= times.length()) {
                    wxHour[k].setText("");
                    wxHourT[k].setText("");
                    wxIcon[k].setImageDrawable(null);
                    continue;
                }
                wxHour[k].setText(k == 0 ? "Now" : times.getString(i).substring(11, 13));
                wxHourT[k].setText((k == 0 ? now : Math.round(temps.getDouble(i))) + "°");
                wxIcon[k].setImageDrawable(new WeatherIcon(wxKind(k == 0 ? code : codes.getInt(i),
                        k == 0 ? isDay : days.getInt(i) == 1)));
            }
            String rain = null;
            for (int i = start + 1; i < Math.min(times.length(), start + 13); i++) {
                if (rainy(codes.getInt(i))) { rain = times.getString(i).substring(11, 16); break; }
            }
            wxRange.setText(rain != null && !rainy(code) ? range + "  ·  Rain from " + rain : range);
        } catch (Exception ignored) {}
    }

    // ---- Quick controls: brightness, volume, sound mode.

    // This TV keeps its backlight (0 to 100) in the setting picture_backlight. Writing it needs a
    // permission that is granted once from a computer; without it the slider says how.
    private static final String BACKLIGHT = "picture_backlight";

    private boolean hasTvBacklight() {
        try { Settings.Global.getInt(getContentResolver(), BACKLIGHT); return true; }
        catch (Exception e) { return false; }
    }

    private void setBrightness(int v) {
        v = Math.max(5, v);
        brightPct.setText(v + "%");
        try {
            if (hasTvBacklight()) {
                Settings.Global.putInt(getContentResolver(), BACKLIGHT, v);
            } else {
                if (android.os.Build.VERSION.SDK_INT >= 23 && !Settings.System.canWrite(this)) throw new SecurityException();
                Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, Math.round(v * 2.55f));
            }
        } catch (Exception e) {
            if (!warnedWrite) {
                warnedWrite = true;
                android.widget.Toast.makeText(this, "Brightness needs a one-time permission: run the grant command from the PC.",
                        android.widget.Toast.LENGTH_LONG).show();
            }
        }
    }

    private void setVolume(int v) {
        volPct.setText(v + "%");
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        try { am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(v * max / 100f), 0); }
        catch (Exception ignored) {}
    }

    // Shows what the TV is set to now.
    private void syncSliders() {
        if (brightSlider == null) return;
        try {
            int v;
            if (hasTvBacklight()) v = Math.max(0, Math.min(100, Settings.Global.getInt(getContentResolver(), BACKLIGHT)));
            else v = Math.min(100, Math.round(Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 178) / 2.55f));
            brightSlider.set(v);
            brightPct.setText(v + "%");
        } catch (Exception ignored) {}
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            int v = Math.round(100f * am.getStreamVolume(AudioManager.STREAM_MUSIC) / max);
            volSlider.set(v);
            volPct.setText(v + "%");
        }
        paintPicChips();
    }

    // The sound modes shape the TV's equaliser. Not every TV lets an app do that: when it doesn't, the
    // chips give way to one button that opens the TV's own sound page.
    private boolean soundSupported() {
        if (!eqChecked) {
            eqChecked = true;
            try {
                eq = new Equalizer(0, 0);
                eqOk = eq.getNumberOfBands() > 0;
            } catch (Throwable t) {
                eqOk = false;
            }
        }
        return eqOk;
    }

    private void applySound(int mode) {
        if (!soundSupported()) return;
        try {
            short[] range = eq.getBandLevelRange();
            short bands = eq.getNumberOfBands();
            for (short b = 0; b < bands; b++) {
                int hz = eq.getCenterFreq(b) / 1000;
                float f = 0f; // -1 to 1: how much to cut or lift this band
                if (mode == 1) f = hz < 150 ? 0.8f : hz > 6000 ? 0.5f : 0.1f;                           // Movie: deep and bright
                else if (mode == 2) f = hz < 250 ? 0.5f : hz > 4000 ? 0.5f : -0.1f;                      // Music: lively
                else if (mode == 3) f = hz < 200 ? -0.7f : (hz >= 1000 && hz <= 4000) ? 0.7f : hz > 8000 ? -0.3f : 0f; // Voice
                int level = f >= 0 ? Math.round(f * range[1]) : Math.round(-f * range[0]);
                eq.setBandLevel(b, (short) Math.max(range[0], Math.min(range[1], level)));
            }
            eq.setEnabled(mode != 0);
        } catch (Throwable t) {
            // Leave the sound as it is.
        }
    }

    // A slider that takes Left and Right in steps of five. It is a plain view drawn by hand.
    private final class Slider extends View {
        private int value;
        private final Drawable icon;
        private final Pick change;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path clip = new Path();

        Slider(Context c, Drawable icon, Pick change) {
            super(c);
            this.icon = icon;
            this.change = change;
            icon.setColorFilter(0xFF121214, android.graphics.PorterDuff.Mode.SRC_IN);
            setFocusable(true);
        }

        void set(int v) {
            value = Math.max(0, Math.min(100, v));
            invalidate();
        }

        @Override
        public boolean onKeyDown(int code, KeyEvent ev) {
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                set(value + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -5 : 5));
                change.at(value);
                return true;
            }
            return super.onKeyDown(code, ev);
        }

        @Override
        protected void onFocusChanged(boolean gained, int direction, android.graphics.Rect prev) {
            super.onFocusChanged(gained, direction, prev);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            float pad = h * 0.12f, th = h - 2 * pad, r = th / 2f;
            rect.set(pad, pad, w - pad, h - pad);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x38FFFFFF);
            cv.drawRoundRect(rect, r, r, paint);
            clip.reset();
            clip.addRoundRect(rect, r, r, Path.Direction.CW);
            cv.save();
            cv.clipPath(clip);
            paint.setColor(0xFFFFFFFF);
            cv.drawRect(pad, pad, pad + th + (w - 2 * pad - th) * value / 100f, h - pad, paint);
            cv.restore();
            int is = Math.round(th * 0.6f), ix = Math.round(pad + th * 0.2f), iy = Math.round(pad + th * 0.2f);
            icon.setBounds(ix, iy, ix + is, iy + is);
            icon.draw(cv);
            if (isFocused()) {
                float sw = Math.max(2f, h * 0.06f);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(sw);
                paint.setColor(0xFFFFFFFF);
                rect.set(sw / 2, sw / 2, w - sw / 2, h - sw / 2);
                cv.drawRoundRect(rect, h / 2f, h / 2f, paint);
            }
        }
    }

    // Small weather pictures drawn in code, on a 100 by 100 grid.
    static final class WeatherIcon extends Drawable {
        static final int SUN = 0, MOON = 1, CLOUD = 2, PARTLY = 3, RAIN = 4, SNOW = 5, STORM = 6;
        private final int kind;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        WeatherIcon(int kind) {
            this.kind = kind;
            p.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        public void draw(Canvas c) {
            float s = getBounds().width() / 100f;
            c.save();
            c.translate(getBounds().left, getBounds().top);
            c.scale(s, s);
            switch (kind) {
                case SUN: sun(c, 50, 50, 1f); break;
                case MOON: moon(c); break;
                case CLOUD: cloud(c, 0, 8); break;
                case PARTLY: sun(c, 34, 34, 0.75f); cloud(c, 6, 16); break;
                case RAIN: cloud(c, 0, -4); drops(c); break;
                case SNOW: cloud(c, 0, -4); flakes(c); break;
                default: cloud(c, 0, -4); bolt(c); break;
            }
            c.restore();
        }

        private void sun(Canvas c, float cx, float cy, float k) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFD60A);
            c.drawCircle(cx, cy, 18 * k, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(6 * k);
            for (int a = 0; a < 8; a++) {
                double ang = Math.toRadians(a * 45);
                float cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
                c.drawLine(cx + cs * 27 * k, cy + sn * 27 * k, cx + cs * 38 * k, cy + sn * 38 * k, p);
            }
        }

        private void cloud(Canvas c, float dx, float dy) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(35 + dx, 58 + dy, 15, p);
            c.drawCircle(54 + dx, 46 + dy, 21, p);
            c.drawCircle(72 + dx, 57 + dy, 16, p);
            r.set(22 + dx, 55 + dy, 88 + dx, 74 + dy);
            c.drawRoundRect(r, 9, 9, p);
        }

        private void drops(Canvas c) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(5);
            p.setColor(0xFF64B5FF);
            c.drawLine(38, 78, 32, 91, p);
            c.drawLine(54, 78, 48, 91, p);
            c.drawLine(70, 78, 64, 91, p);
        }

        private void flakes(Canvas c) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(36, 82, 3.5f, p);
            c.drawCircle(54, 90, 3.5f, p);
            c.drawCircle(72, 82, 3.5f, p);
        }

        private void bolt(Canvas c) {
            Path b = new Path();
            b.moveTo(56, 64);
            b.lineTo(42, 84);
            b.lineTo(52, 84);
            b.lineTo(46, 98);
            b.lineTo(66, 76);
            b.lineTo(55, 76);
            b.lineTo(62, 64);
            b.close();
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFD60A);
            c.drawPath(b, p);
        }

        private void moon(Canvas c) {
            Path a = new Path();
            a.addCircle(46, 52, 30, Path.Direction.CW);
            Path b = new Path();
            b.addCircle(60, 42, 26, Path.Direction.CW);
            a.op(b, Path.Op.DIFFERENCE);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFFFFF);
            c.drawPath(a, p);
        }

        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { p.setColorFilter(cf); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return 100; }
        @Override public int getIntrinsicHeight() { return 100; }
    }
}
