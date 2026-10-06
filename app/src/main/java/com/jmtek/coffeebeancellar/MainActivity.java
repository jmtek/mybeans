package com.jmtek.coffeebeancellar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_BEAN_IMAGES = 301;
    private static final int BG = Color.argb(88, 255, 255, 255);
    private static final int INK = Color.rgb(255, 250, 238);
    private static final int MUTED = Color.argb(202, 255, 247, 226);
    private static final int LINE = Color.argb(86, 255, 255, 255);
    private static final int CARD = Color.argb(74, 34, 25, 20);
    private static final int GREEN = Color.rgb(34, 157, 139);
    private static final int GREEN_DARK = Color.rgb(205, 255, 244);
    private static final int GREEN_SOFT = Color.argb(92, 49, 207, 184);
    private static final int CREAM = Color.argb(58, 255, 255, 255);
    private static final int SAND = Color.argb(48, 255, 255, 255);
    private static final int PANEL = Color.argb(52, 255, 255, 255);
    private static final int AMBER = Color.rgb(255, 197, 122);
    private static final int AMBER_SOFT = Color.argb(80, 255, 173, 86);
    private static final int ROSE = Color.rgb(255, 154, 142);
    private static final int ROSE_SOFT = Color.argb(92, 255, 116, 102);
    private static final DateTimeFormatter BREW_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] BEAN_TYPES = {
            "铁皮卡 / Typica",
            "波旁 / Bourbon",
            "瑰夏 / Gesha",
            "象豆 / Maragogipe",
            "帕卡斯 / Pacas",
            "卡杜拉 / Caturra",
            "埃塞原生种 / Ethiopian Heirloom",
            "薇拉萨奇 / Villa Sarchi",
            "红波旁 / Red Bourbon",
            "黄波旁 / Yellow Bourbon",
            "帕卡马拉 / Pacamara",
            "新世界 / Mundo Novo",
            "帝汶杂交种 / Timor Hybrid",
            "卡杜艾 / Catuai",
            "卡蒂姆群 / Catimor",
            "萨奇莫群 / Sarchimor",
            "T5296 系"
    };
    private static final String DEFAULT_BEAN_TYPE = "铁皮卡 / Typica";

    private CoffeeDb db;
    private LinearLayout root;
    private LinearLayout content;
    private FrameLayout shell;
    private String tab = "beans";
    private long brewBeanId = -1;
    private boolean beanGalleryMode = false;
    private String beanTypeFilter = "全部";
    private String roastFilter = "全部";
    private String processFilter = "全部";
    private String roasterFilter = "全部";
    private ImageUploadField pendingImageField;
    private LruCache<String, Bitmap> imageCache;
    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(2);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        int cacheKb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 10);
        imageCache = new LruCache<String, Bitmap>(cacheKb) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return Math.max(1, value.getByteCount() / 1024);
            }
        };
        db = new CoffeeDb(this);
        db.seedIfEmpty();
        buildShell();
        render();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_BEAN_IMAGES || resultCode != RESULT_OK || pendingImageField == null || data == null) return;
        List<String> picked = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount() && picked.size() < 3; i++) {
                addPickedImage(picked, clip.getItemAt(i).getUri(), data);
            }
        } else {
            addPickedImage(picked, data.getData(), data);
        }
        pendingImageField.appendImages(picked);
        Toast.makeText(this, "已添加 " + picked.size() + " 张图片，共 " + pendingImageField.images.size() + " 张", Toast.LENGTH_SHORT).show();
    }

    private void addPickedImage(List<String> picked, Uri uri, Intent data) {
        if (uri == null || picked.size() >= 3) return;
        try {
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri, flags & Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}
        String value = uri.toString();
        if (!picked.contains(value)) picked.add(value);
    }

    private void buildShell() {
        shell = new FrameLayout(this);
        ImageView bg = new ImageView(this);
        bg.setImageResource(getResources().getIdentifier("coffee_background", "drawable", getPackageName()));
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        shell.addView(bg, new FrameLayout.LayoutParams(-1, -1));

        View veil = new View(this);
        veil.setBackgroundColor(Color.argb(176, 22, 16, 12));
        shell.addView(veil, new FrameLayout.LayoutParams(-1, -1));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.TRANSPARENT);
        shell.addView(root, new FrameLayout.LayoutParams(-1, -1));
        setContentView(shell);
    }

    private void render() {
        root.removeAllViews();
        root.addView(header(), new LinearLayout.LayoutParams(-1, dp(20)));

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(88));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        if ("beans".equals(tab)) renderBeans();
        if ("brew".equals(tab)) renderBrew();
        if ("stock".equals(tab)) renderStock();
        if ("stats".equals(tab)) renderStats();
        if ("alerts".equals(tab)) renderAlerts();

        root.addView(nav(), new LinearLayout.LayoutParams(-1, dp(72)));
    }

    private View header() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(10), dp(18), 0);
        box.setBackgroundColor(Color.TRANSPARENT);
        return box;
    }

    private View nav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(6), dp(4), dp(6), dp(5));
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setBackground(round(Color.argb(96, 36, 28, 24), dp(24), Color.argb(102, 255, 255, 255)));
        addNav(nav, "beans", "◎", "我的豆", false);
        addNav(nav, "stock", "▣", "剩多少", false);
        addNav(nav, "brew", "☕", "喝一杯", true);
        addNav(nav, "stats", "◌", "算一算", false);
        addNav(nav, "alerts", "!", "注意啦", false);
        return nav;
    }

    private void addNav(LinearLayout nav, String key, String icon, String label, boolean center) {
        boolean active = key.equals(tab);
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(0, center ? dp(1) : dp(2), 0, 0);
        int fill = center
                ? (active ? Color.argb(230, 34, 157, 139) : Color.argb(150, 255, 197, 122))
                : (active ? Color.argb(170, 34, 157, 139) : Color.TRANSPARENT);
        int stroke = active || center ? Color.argb(center ? 128 : 80, 255, 255, 255) : 0;
        item.setBackground(round(fill, dp(center ? 24 : 18), stroke));
        item.setOnClickListener(v -> {
            tab = key;
            render();
        });

        TextView glyph = text(icon, center ? 30 : 20, center ? Color.WHITE : (active ? Color.WHITE : MUTED), true);
        glyph.setGravity(Gravity.CENTER);
        TextView copy = text(label, center ? 12 : 11, active || center ? Color.WHITE : MUTED, active || center);
        copy.setGravity(Gravity.CENTER);
        item.addView(glyph, new LinearLayout.LayoutParams(-1, dp(center ? 33 : 24)));
        item.addView(copy, new LinearLayout.LayoutParams(-1, dp(20)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, center ? dp(64) : -1, center ? 1.22f : 1f);
        lp.setMargins(dp(center ? 4 : 2), center ? 0 : dp(6), dp(center ? 4 : 2), center ? 0 : dp(6));
        nav.addView(item, lp);
    }

    private void renderBeans() {
        content.addView(beansHero(), fullMargin());

        content.addView(beanBrowseControls(), fullMargin());

        List<Bean> active = new ArrayList<>();
        List<Bean> finished = new ArrayList<>();
        for (Bean bean : db.beanGroups()) {
            if (!matchesBeanFilter(bean)) continue;
            if (isConsumed(bean)) finished.add(bean);
            else active.add(bean);
        }

        addBeanCards(active);
        addConsumedBeanSection(finished);
    }

    private View beansHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(16), dp(18), dp(12), dp(12));
        hero.setBackground(round(Color.argb(68, 255, 255, 255), dp(16), LINE));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text("我的豆", 28, INK, true));
        TextView sub = text("按产区、处理法和风味沉淀口味地图", 13, MUTED, false);
        sub.setPadding(0, dp(2), 0, 0);
        copy.addView(sub);
        top.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

        TextView add = circleButton("+", Color.WHITE, GREEN, GREEN_DARK);
        add.setTextSize(24);
        add.setContentDescription("新增豆子");
        add.setOnClickListener(v -> showAddBeanDialog());
        top.addView(add, new LinearLayout.LayoutParams(dp(48), dp(48)));
        hero.addView(top);

        return hero;
    }

    private void addBeanCards(List<Bean> beans) {
        if (beans.isEmpty()) {
            TextView empty = text("没有符合筛选条件的豆子", 13, MUTED, true);
            empty.setPadding(0, dp(8), 0, dp(8));
            content.addView(empty);
            return;
        }
        if (!beanGalleryMode) {
            for (Bean bean : beans) content.addView(beanCard(bean), fullMargin());
            return;
        }
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        int cell = (getResources().getDisplayMetrics().widthPixels - dp(40)) / 2;
        for (int i = 0; i < beans.size(); i++) {
            Bean bean = beans.get(i);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cell;
            lp.setMargins(i % 2 == 0 ? 0 : dp(8), dp(4), 0, dp(4));
            grid.addView(beanGalleryCard(bean), lp);
        }
        content.addView(grid, fullMargin());
    }

    private View beanBrowseControls() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        box.setBackground(round(PANEL, dp(16), LINE));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text("快速筛选", 14, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView list = iconButton("☷", GREEN_DARK, CREAM, LINE);
        list.setContentDescription("列表视图");
        TextView gallery = iconButton("▦", GREEN_DARK, CREAM, LINE);
        gallery.setContentDescription("画廊视图");
        list.setOnClickListener(v -> { beanGalleryMode = false; render(); });
        gallery.setOnClickListener(v -> { beanGalleryMode = true; render(); });
        if (!beanGalleryMode) list.setBackground(round(GREEN_SOFT, dp(12), GREEN));
        else gallery.setBackground(round(GREEN_SOFT, dp(12), GREEN));
        top.addView(list, new LinearLayout.LayoutParams(dp(38), dp(34)));
        LinearLayout.LayoutParams galleryLp = new LinearLayout.LayoutParams(dp(38), dp(34));
        galleryLp.setMargins(dp(6), 0, 0, 0);
        top.addView(gallery, galleryLp);
        box.addView(top);
        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setPadding(0, dp(8), 0, 0);
        addFilterButton(filters, "豆种", beanTypeFilter, 0);
        addFilterButton(filters, "烘焙", roastFilter, 1);
        addFilterButton(filters, "处理", processFilter, 2);
        addFilterButton(filters, "品牌", roasterFilter, 3);
        box.addView(filters);
        return box;
    }

    private void addFilterButton(LinearLayout row, String label, String value, int which) {
        Button button = subtleButton(label + ("全部".equals(value) ? " ⌄" : " ●"));
        button.setTextSize(11);
        button.setOnClickListener(v -> showBeanFilterDialog(label, which));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1);
        lp.setMargins(row.getChildCount() == 0 ? 0 : dp(5), 0, 0, 0);
        row.addView(button, lp);
    }

    private void showBeanFilterDialog(String label, int which) {
        List<String> options = new ArrayList<>();
        options.add("全部");
        if (which == 0) {
            options.add("拼配");
            Collections.addAll(options, BEAN_TYPES);
        }
        for (Bean bean : db.beanGroups()) {
            String value = which == 0 ? (bean.isBlend ? "拼配" : normalizeBeanTypes(bean.beanType)) : which == 1 ? bean.roastLevel : which == 2 ? bean.process : bean.roaster;
            if (value != null && !value.trim().isEmpty() && !options.contains(value)) options.add(value);
        }
        new AlertDialog.Builder(this).setTitle("筛选" + label)
                .setItems(options.toArray(new String[0]), (d, position) -> {
                    String selected = options.get(position);
                    if (which == 0) beanTypeFilter = selected;
                    else if (which == 1) roastFilter = selected;
                    else if (which == 2) processFilter = selected;
                    else roasterFilter = selected;
                    render();
                }).show();
    }

    private boolean matchesBeanFilter(Bean bean) {
        return ("全部".equals(beanTypeFilter) || ("拼配".equals(beanTypeFilter) ? bean.isBlend : beanTypeFilter.equals(normalizeBeanTypes(bean.beanType))))
                && ("全部".equals(roastFilter) || roastFilter.equals(bean.roastLevel))
                && ("全部".equals(processFilter) || processFilter.equals(bean.process))
                && ("全部".equals(roasterFilter) || roasterFilter.equals(bean.roaster));
    }

    private LinearLayout.LayoutParams badgeMargin() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(6), 0, 0, 0);
        return lp;
    }

    private View beanGalleryCard(Bean bean) {
        LinearLayout card = card();
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.addView(beanThumb(bean, dp(146), dp(14)), new LinearLayout.LayoutParams(-1, dp(146)));
        TextView name = text(bean.name, 15, INK, true);
        name.setPadding(0, dp(9), 0, 0);
        name.setMaxLines(2);
        card.addView(name);
        TextView meta = text(bean.roaster + " · " + (bean.isBlend ? "拼配 · " : "") + bean.beanType, 11, MUTED, false);
        meta.setPadding(0, dp(3), 0, 0);
        meta.setMaxLines(2);
        card.addView(meta);
        LinearLayout foot = new LinearLayout(this);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        TextView roast = statusBadge(bean.roastLevel, AMBER_SOFT, AMBER);
        foot.addView(roast);
        TextView process = statusBadge(bean.process, GREEN_SOFT, GREEN_DARK);
        LinearLayout.LayoutParams processLp = new LinearLayout.LayoutParams(0, -2, 1);
        processLp.setMargins(dp(5), 0, 0, 0);
        foot.addView(process, processLp);
        foot.addView(stockRing(bean), new LinearLayout.LayoutParams(dp(36), dp(36)));
        LinearLayout.LayoutParams footLp = new LinearLayout.LayoutParams(-1, dp(44));
        footLp.setMargins(0, dp(5), 0, 0);
        card.addView(foot, footLp);
        card.setOnClickListener(v -> showEditBeanDialog(bean));
        return card;
    }

    private void addConsumedBeanSection(List<Bean> beans) {
        if (beans.isEmpty()) return;
        TextView heading = text("已消耗完", 15, MUTED, true);
        heading.setPadding(0, dp(14), 0, dp(2));
        content.addView(heading);
        TextView amount = text(beans.size() + " 款", 12, MUTED, false);
        amount.setPadding(0, 0, 0, dp(4));
        content.addView(amount);
        addBeanCards(beans);
    }

    private View beanCard(Bean bean) {
        LinearLayout card = card();
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        top.addView(beanThumb(bean, dp(48), dp(18)), new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(dp(12), 0, 0, 0);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(text(bean.name, 20, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        titleRow.addView(stockRing(bean), new LinearLayout.LayoutParams(dp(48), dp(48)));
        names.addView(titleRow);
        TextView meta = text(bean.roaster + " · " + (bean.isBlend ? "拼配 · " : "") + bean.beanType + " · " + bean.origin + " · " + bean.process, 12, MUTED, false);
        meta.setPadding(0, dp(3), 0, 0);
        names.addView(meta);
        if (!isBlank(bean.blendDetails)) {
            TextView blend = text("拼配：" + bean.blendDetails, 11, GREEN_DARK, false);
            blend.setPadding(0, dp(3), 0, 0);
            blend.setMaxLines(2);
            names.addView(blend);
        }
        TextView roast = statusBadge(bean.roastLevel, AMBER_SOFT, AMBER);
        LinearLayout.LayoutParams roastLp = new LinearLayout.LayoutParams(-2, -2);
        roastLp.setMargins(0, dp(7), 0, 0);
        names.addView(roast, roastLp);
        top.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(top);

        View gallery = imageGallery(bean);
        if (gallery != null) card.addView(gallery);

        card.addView(tagRow(bean.flavorTags));

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        addMetricBlock(metrics,
                String.format(Locale.CHINA, "%.0f / %.0fg", bean.remainingGram, bean.totalGram),
                "库存",
                bean.totalGram > 0 ? String.format(Locale.CHINA, "¥%.2f/g", bean.price / bean.totalGram) : "-/g",
                "克重价",
                GREEN_SOFT,
                GREEN_DARK);
        addMetricBlock(metrics,
                drinkingWindow(bean),
                "赏味期",
                "约 " + Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), date(cleanBestBefore(bean)))) + " 天",
                "剩余天数",
                AMBER_SOFT,
                AMBER);
        card.addView(metrics);

        LinearLayout recipe = new LinearLayout(this);
        recipe.setOrientation(LinearLayout.HORIZONTAL);
        recipe.setGravity(Gravity.CENTER_VERTICAL);
        recipe.setPadding(dp(12), dp(10), dp(10), dp(10));
        recipe.setBackground(round(PANEL, dp(12), LINE));
        LinearLayout recipeText = new LinearLayout(this);
        recipeText.setOrientation(LinearLayout.VERTICAL);
        recipeText.addView(text("最佳配方", 12, INK, true));
        recipeText.addView(text(bestRecipe(bean), 12, MUTED, false));
        recipe.addView(recipeText, new LinearLayout.LayoutParams(0, -2, 1));
        TextView addRecipe = statusBadge("添加", Color.WHITE, GREEN_DARK);
        addRecipe.setClickable(true);
        addRecipe.setFocusable(true);
        if (isConsumed(bean)) {
            addRecipe.setText("已空");
            addRecipe.setTextColor(MUTED);
            addRecipe.setBackground(round(CREAM, dp(12), LINE));
        } else {
            addRecipe.setOnClickListener(v -> openBrew(drinkBatch(bean)));
        }
        recipe.addView(addRecipe);
        LinearLayout.LayoutParams recipeLp = new LinearLayout.LayoutParams(-1, -2);
        recipeLp.setMargins(0, dp(10), 0, 0);
        card.addView(recipe, recipeLp);
        card.addView(batchList(bean));

        Button drink = primaryButton(isConsumed(bean) ? "已消耗完" : "☕ 记录一杯");
        drink.setEnabled(!isConsumed(bean));
        if (isConsumed(bean)) {
            drink.setTextColor(MUTED);
            drink.setBackground(round(CREAM, dp(14), LINE));
        } else {
            drink.setOnClickListener(v -> openBrew(drinkBatch(bean)));
        }
        LinearLayout.LayoutParams drinkLp = new LinearLayout.LayoutParams(-1, dp(44));
        drinkLp.setMargins(0, dp(12), 0, dp(8));
        card.addView(drink, drinkLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button stock = subtleButton("同款新批次");
        stock.setElevation(0);
        stock.setStateListAnimator(null);
        stock.setOnClickListener(v -> showNewBatchDialog(bean));
        TextView detail = iconButton("◷", GREEN_DARK, CREAM, Color.rgb(220, 207, 185));
        detail.setContentDescription("时间线");
        detail.setOnClickListener(v -> showTimeline(bean));
        TextView edit = iconButton("✎", GREEN_DARK, CREAM, Color.rgb(220, 207, 185));
        edit.setContentDescription("编辑");
        edit.setOnClickListener(v -> showEditBeanDialog(bean));
        TextView delete = iconButton("×", ROSE, Color.rgb(253, 238, 240), Color.rgb(238, 194, 199));
        delete.setContentDescription("删除");
        delete.setOnClickListener(v -> confirmDelete(bean));
        actions.addView(stock, actionTextParams());
        actions.addView(detail, iconParams());
        actions.addView(edit, iconParams());
        actions.addView(delete, iconParams());
        card.addView(actions);
        return card;
    }

    private void renderBrew() {
        Bean pick = brewBeanId > 0 ? db.beanById(brewBeanId) : null;
        if (pick == null || pick.remainingGram <= 0) pick = db.recommendedBean();
        if (pick == null) {
            pageTitle("喝一杯", "先添加一包咖啡豆，再开始记录冲煮");
            content.addView(emptyAlertCard(), fullMargin());
            return;
        }
        brewBeanId = pick.id;

        pageTitle("喝一杯", "记录这一杯的参数、风味和具体冲煮时段");

        final Bean currentPick = pick;
        BrewInputs inputs = new BrewInputs();
        inputs.method = "手冲";
        inputs.grind = "中细";
        inputs.score = 42;
        inputs.noteTags = new ArrayList<>();

        content.addView(brewBeanPicker(currentPick), fullMargin());
        TextView hint = text("◷ 参数已按上一杯预填，按需调整", 12, MUTED, true);
        hint.setPadding(dp(6), dp(4), 0, dp(8));
        content.addView(hint);

        EditText dose = numberInput("粉量", "15");
        EditText water = numberInput("水量", "240");
        EditText temp = numberInput("水温", "92");
        EditText time = input("萃取时间", "2:30");
        content.addView(formSection("冲煮方式", segmented("method", new String[]{"手冲", "意式", "爱乐压", "冷萃"}, inputs, () -> {
            dose.setText("20"); water.setText("36"); time.setText("0:30");
        })), fullMargin());

        EditText brewTime = input("冲煮时间", LocalTime.now().format(BREW_TIME_FORMAT));
        content.addView(formSection("记录时间", labeled("时间", brewTime)), fullMargin());

        TextView ratio = compactRatioField(dose, water);
        content.addView(formSection("粉水", twoColumns(labeled("粉量", withSuffix(dose, "g")), labeled("水量", withSuffix(water, "ml"))), ratio), fullMargin());

        content.addView(formSection("研磨与水温",
                chipGroup("grind", new String[]{"细", "中细", "中", "粗"}, inputs, true),
                twoColumns(labeled("水温", withSuffix(temp, "°C")), labeled("萃取时间", withSuffix(time, "分:秒")))), fullMargin());

        content.addView(ratingSection(inputs), fullMargin());

        EditText note = input("还有什么想记的", "");
        ImageUploadField brewImages = imageUploadField("");
        content.addView(formSection("风味笔记",
                chipGroup("notes", new String[]{"甜感清楚", "尾段干净", "明亮酸", "醇厚", "偏苦", "偏酸"}, inputs, false),
                labeled("补充", note), labeled("冲煮图片", brewImages.box)), fullMargin());

        TextView consume = text("", 13, MUTED, true);
        consume.setGravity(Gravity.CENTER);
        consume.setPadding(0, dp(28), 0, dp(8));
        Runnable refreshConsume = () -> consume.setText(String.format(Locale.CHINA, "将从库存扣除 %sg，剩余 %sg → %sg",
                fmt(num(dose, 15)), fmt(currentPick.remainingGram), fmt(Math.max(0, currentPick.remainingGram - num(dose, 15)))));
        dose.addTextChangedListener(simpleWatcher(refreshConsume));
        refreshConsume.run();
        content.addView(consume);

        Button save = primaryButton("保存并扣库存");
        save.setTextSize(16);
        save.setOnClickListener(v -> {
            String finalNote = brewNote(inputs.noteTags, note.getText().toString());
            db.addBrew(currentPick.id, LocalDate.now().toString(), brewTime.getText().toString(), inputs.method, num(dose, 15), num(water, 240),
                    inputs.grind, (int) num(temp, 92), time.getText().toString(), inputs.score / 10.0, finalNote, joinImages(brewImages.images));
            Toast.makeText(this, "已记录并扣减库存", Toast.LENGTH_SHORT).show();
            render();
        });
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(-1, dp(58));
        saveLp.setMargins(0, dp(4), 0, dp(16));
        content.addView(save, saveLp);

        LinearLayout recentTitle = new LinearLayout(this);
        recentTitle.setOrientation(LinearLayout.HORIZONTAL);
        recentTitle.setGravity(Gravity.CENTER_VERTICAL);
        recentTitle.setPadding(0, dp(8), 0, dp(4));
        recentTitle.addView(text("最近 7 天冲煮", 20, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(recentTitle);
        content.addView(brewGroupedByDayList(db.recentBrews(7), true), fullMargin());
    }

    private void renderStock() {
        pageTitle("剩多少", "优先处理开封久、赏味尾段和余量较低的豆子");
        List<Bean> beans = db.beanGroupsByPriority();
        List<Bean> active = new ArrayList<>();
        List<Bean> finished = new ArrayList<>();
        for (Bean bean : beans) {
            if (isConsumed(bean)) finished.add(bean);
            else active.add(bean);
        }
        content.addView(stockOverview(active, finished), fullMargin());
        content.addView(stockConsumptionCard(), fullMargin());
        sectionTitle("未来 30 天", "养豆完成、接近赏味期与赏味截止提醒");
        content.addView(futureBeanTimeline(beans), fullMargin());

        addStockSection("优先处理", active, "priority");
        addStockSection("正在享用", active, "ready");
        addStockSection("养豆中", active, "resting");
        addStockSection("已过峰值", active, "past");
        addStockSection("已消耗完", finished, "consumed");
    }

    private View futureBeanTimeline(List<Bean> beans) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        LocalDate today = LocalDate.now();
        List<FutureBeanEvent> events = new ArrayList<>();
        for (Bean bean : beans) {
            LocalDate ready = date(bean.roastDate).plusDays(7);
            long readyDays = ChronoUnit.DAYS.between(today, ready);
            if (readyDays >= -7 && readyDays <= 30) {
                String detail = readyDays < 0 ? "已养好 " + (-readyDays) + " 天" : readyDays == 0 ? "今天养豆完成" : "还有 " + readyDays + " 天养好豆";
                events.add(new FutureBeanEvent(ready, bean.name + " 养豆完成", detail, bean.roaster + " · " + bean.process, "养", GREEN_SOFT, GREEN_DARK));
            }
            if (isConsumed(bean)) {
                LocalDate emptyDate = db.lastBrewDate(bean.batchIds());
                long emptyDays = ChronoUnit.DAYS.between(today, emptyDate);
                if (emptyDays >= -7 && emptyDays <= 0) {
                    events.add(new FutureBeanEvent(emptyDate, bean.name + " 已喝完", "已消耗完，不再提示赏味期", bean.roaster + " · " + bean.process, "空", CREAM, MUTED));
                }
                continue;
            }
            LocalDate best = date(cleanBestBefore(bean));
            long days = ChronoUnit.DAYS.between(today, best);
            if (days < -7 || days > 30) continue;
            String detail = days < 0 ? "已过赏味期 " + (-days) + " 天" : days == 0 ? "今天到达赏味节点" : "还有 " + days + " 天 · " + drinkingWindow(bean);
            detail += " · 当前约剩 " + bestRecipeCups(bean) + " 杯";
            events.add(new FutureBeanEvent(best, bean.name + " 赏味节点", detail, bean.roaster + " · " + bean.process, "赏", AMBER_SOFT, AMBER));
        }
        Collections.sort(events, (a, b) -> a.date.compareTo(b.date));
        for (int i = 0; i < events.size(); i++) {
            FutureBeanEvent event = events.get(i);
            list.addView(timelineRow(event.date.toString(), event.title, event.detail, event.note, event.marker, event.fill, event.ink, i == events.size() - 1));
        }
        if (list.getChildCount() == 0) list.addView(text("过去 7 天到未来 30 天没有需要特别留意的节点", 13, MUTED, true));
        return list;
    }

    private View stockOverview(List<Bean> beans, List<Bean> finished) {
        int totalCups = 0;
        int golden = 0;
        int low = 0;
        int resting = 0;
        double totalGram = 0;
        for (Bean bean : beans) {
            totalCups += bestRecipeCups(bean);
            totalGram += bean.remainingGram;
            if ("黄金期".equals(drinkingWindow(bean))) golden++;
            if ("养豆中".equals(drinkingWindow(bean))) resting++;
            if (bean.remainingGram < 45) low++;
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        box.setBackground(round(PANEL, dp(16), LINE));

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.addView(compactStat("在库豆子", beans.size() + " 款", PANEL, INK, LINE));
        grid.addView(compactStat("还可冲", totalCups + " 杯", GREEN_SOFT, GREEN_DARK, LINE));
        grid.addView(compactStat("黄金期", golden + " 款", AMBER_SOFT, AMBER, LINE));
        grid.addView(compactStat("总余量", fmt(totalGram) + "g", ROSE_SOFT, ROSE, LINE));
        grid.addView(compactStat("养豆中", resting + " 款", AMBER_SOFT, AMBER, LINE));
        grid.addView(compactStat("已消耗完", finished.size() + " 款", CREAM, MUTED, LINE));
        box.addView(grid);

        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setPadding(0, dp(10), 0, 0);
        legend.addView(legendDot("低库存", ROSE));
        legend.addView(legendDot("黄金期", GREEN));
        legend.addView(legendDot("养豆", AMBER));
        legend.addView(legendDot("待消耗", MUTED));
        box.addView(legend);

        if (low > 0) {
            TextView warn = text("有 " + low + " 款低库存，建议优先安排冲煮", 12, ROSE, true);
            warn.setPadding(0, dp(8), 0, 0);
            box.addView(warn);
        }
        return box;
    }

    private void addStockSection(String title, List<Bean> beans, String group) {
        int count = 0;
        for (Bean bean : beans) if (stockGroup(bean).equals(group)) count++;
        if (count == 0) return;

        TextView heading = text(title, 15, "priority".equals(group) ? ROSE : "consumed".equals(group) ? MUTED : GREEN_DARK, true);
        heading.setPadding(0, dp(14), 0, dp(2));
        content.addView(heading);
        TextView amount = text(count + " 款", 12, MUTED, false);
        amount.setPadding(0, 0, 0, dp(4));
        content.addView(amount);

        for (Bean bean : beans) {
            if (!stockGroup(bean).equals(group)) continue;
            content.addView(stockCard(bean), fullMargin());
        }
    }

    private View stockCard(Bean bean) {
        LinearLayout row = card();
        row.setPadding(dp(12), dp(12), dp(12), dp(12));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView cups = text(isConsumed(bean) ? "空\n袋" : bestRecipeCups(bean) + "\n杯", 15, statusColor(bean), true);
        cups.setGravity(Gravity.CENTER);
        cups.setBackground(round(CREAM, dp(28), statusColor(bean)));
        top.addView(cups, new LinearLayout.LayoutParams(dp(56), dp(56)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(12), 0, 0, 0);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(text(bean.name, 18, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        titleRow.addView(statusBadge(batchCount(bean), CREAM, MUTED));
        info.addView(titleRow);
        info.addView(text(stockStatus(bean), 13, statusColor(bean), true));
        info.addView(text(bean.beanType + " · 烘焙 " + bean.roastDate + " · 赏味至 " + cleanBestBefore(bean), 12, MUTED, false));
        top.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(top);

        View gallery = imageGallery(bean);
        if (gallery != null) row.addView(gallery);

        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax((int) Math.max(bean.totalGram, 1));
        bar.setProgress((int) Math.max(0, bean.remainingGram));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(-1, dp(8));
        barLp.setMargins(0, dp(10), 0, dp(6));
        row.addView(bar, barLp);

        row.addView(text(bean.origin + " · " + bean.process + " · " + fmt(bean.remainingGram) + "/" + fmt(bean.totalGram) + "g · " + bestRecipe(bean), 12, MUTED, false));
        row.addView(batchList(bean));
        return row;
    }

    private View beanThumb(Bean bean, int size, int radius) {
        List<String> images = displayImages(bean);
        if (!images.isEmpty()) {
            ImageView photo = new ImageView(this);
            photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            photo.setBackground(round(PANEL, radius, LINE));
            photo.setClipToOutline(true);
            setCachedImage(photo, images.get(0), size);
            return photo;
        }
        TextView mark = text(bean.origin.substring(0, Math.min(1, bean.origin.length())), size <= dp(48) ? 20 : 17, Color.WHITE, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(round(accentFor(bean.process), radius, Color.TRANSPARENT));
        return mark;
    }

    private View imageGallery(Bean bean) {
        List<String> images = allBeanImages(bean);
        return imageGallery(images);
    }

    private View imageGallery(String imageUris) {
        return imageGallery(imageList(imageUris));
    }

    private View imageGallery(List<String> images) {
        if (images.isEmpty()) return null;
        LinearLayout gallery = new LinearLayout(this);
        gallery.setOrientation(LinearLayout.HORIZONTAL);
        int count = images.size();
        int gap = dp(8);
        gallery.setPadding(0, dp(12), 0, 0);
        for (int i = 0; i < count; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(120), 1);
            lp.setMargins(i == 0 ? 0 : gap, 0, 0, 0);
            gallery.addView(imageTile(images.get(i), dp(160), dp(12)), lp);
        }
        gallery.post(() -> {
            int cell = Math.max(dp(72), (gallery.getWidth() - gap * (count - 1)) / count);
            for (int i = 0; i < gallery.getChildCount(); i++) gallery.getChildAt(i).getLayoutParams().height = cell;
            gallery.requestLayout();
        });
        return gallery;
    }

    private List<String> displayImages(Bean bean) {
        List<String> beanImages = imageList(bean.beanImageUris);
        if (!beanImages.isEmpty()) return beanImages;
        List<String> packageImages = imageList(bean.packageImageUris);
        if (!packageImages.isEmpty()) return packageImages;
        return imageList(bean.imageUris);
    }

    private List<String> allBeanImages(Bean bean) {
        List<String> out = new ArrayList<>();
        for (String uri : imageList(bean.beanImageUris)) if (!out.contains(uri) && out.size() < 3) out.add(uri);
        for (String uri : imageList(bean.packageImageUris)) if (!out.contains(uri) && out.size() < 3) out.add(uri);
        for (String uri : imageList(bean.imageUris)) if (!out.contains(uri) && out.size() < 3) out.add(uri);
        return out;
    }

    private View imageTile(String imageUri, int targetSize, int radius) {
        FrameLayout tile = new FrameLayout(this);
        tile.setPadding(dp(1), dp(1), dp(1), dp(1));
        tile.setBackground(round(PANEL, radius, LINE));
        tile.setClipToOutline(true);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        setCachedImage(image, imageUri, targetSize);
        tile.setOnClickListener(v -> showImagePreview(imageUri));
        tile.addView(image, new FrameLayout.LayoutParams(-1, -1));
        return tile;
    }

    private void showImagePreview(String imageUri) {
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setAdjustViewBounds(true);
        setCachedImage(image, imageUri, getResources().getDisplayMetrics().widthPixels);
        new AlertDialog.Builder(this).setView(image).setPositiveButton("关闭", null).show();
    }

    private void setCachedImage(ImageView image, String imageUri, int targetSize) {
        String key = imageUri + "@" + Math.max(targetSize, dp(48));
        image.setTag(key);
        Bitmap cached = imageCache.get(key);
        if (cached != null) {
            image.setImageBitmap(cached);
            return;
        }
        image.setImageDrawable(null);
        imageExecutor.execute(() -> {
            Bitmap bitmap = decodeBitmap(imageUri, Math.max(targetSize, dp(48)));
            if (bitmap == null) return;
            imageCache.put(key, bitmap);
            image.post(() -> {
                if (key.equals(image.getTag())) image.setImageBitmap(bitmap);
            });
        });
    }

    @Override
    protected void onDestroy() {
        imageExecutor.shutdownNow();
        super.onDestroy();
    }

    private Bitmap decodeBitmap(String imageUri, int targetSize) {
        Uri uri = Uri.parse(imageUri);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        } catch (Exception ignored) {
            return null;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = imageSampleSize(bounds, targetSize, targetSize);
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(in, null, options);
        } catch (Exception ignored) {
            return null;
        }
    }

    private int imageSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int sample = 1;
        while (height / sample > reqHeight * 2 && width / sample > reqWidth * 2) {
            sample *= 2;
        }
        return sample;
    }

    private void renderStats() {
        Stats s = db.stats();
        pageTitle("算一算", "从库存、冲煮、评分、豆种和烘焙度里看见喝豆习惯");

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.addView(statCard("总库存", fmt(s.totalRemaining) + "g"));
        grid.addView(statCard("已记录", s.brewCount + " 杯"));
        grid.addView(statCard("本月消耗", fmt(s.monthUsed) + "g"));
        grid.addView(statCard("平均评分", s.avgScore > 0 ? String.format(Locale.CHINA, "%.1f", s.avgScore) : "-"));
        content.addView(grid, fullMargin());

        content.addView(columnChartCard("近 7 天消耗", "横轴为日期，纵轴为消耗粉量", db.dailyConsumption(7), "g"), fullMargin());
        content.addView(columnChartCard("冲煮时段", "横轴为时间段，纵轴为冲煮杯数", db.hourCounts(), "杯"), fullMargin());
        content.addView(columnChartCard("月度购入开销", "横轴为月份，纵轴为购入开销", db.monthlyPurchaseSpend(), "元"), fullMargin());
        content.addView(distributionChartCard("冲煮方式", "不同器具的使用频次", db.methodCounts(), "杯"), fullMargin());
        content.addView(distributionChartCard("烘焙度分布", "按当前豆子库存批次统计", db.roastLevelCounts(), "款"), fullMargin());
        content.addView(distributionChartCard("豆种分布", "按当前豆子库存批次统计", db.beanTypeCounts(), "款"), fullMargin());
        content.addView(distributionChartCard("克重价分布", "按每克购入价统计", db.priceBuckets(), "款"), fullMargin());
        content.addView(regionMapCard(db.originCounts()), fullMargin());
        content.addView(barChartCard("评分分布", "这段时间的满意度集中在哪", db.scoreBuckets(), "杯"), fullMargin());

        sectionTitle("复购候选", "高分且记录次数更多的豆子会排在前面");
        for (String line : db.rebuyCandidates()) {
            content.addView(textPill(line), fullMargin());
        }
    }

    private void renderAlerts() {
        pageTitle("注意啦", "养豆完成、最佳赏味期、低库存都会聚合在这里");
        List<String> alerts = db.alerts();
        if (alerts.isEmpty()) {
            content.addView(emptyAlertCard(), fullMargin());
            return;
        }
        int urgent = 0;
        for (String alert : alerts) if (alert.contains("低库存") || alert.contains("过赏味") || alert.contains("超过")) urgent++;

        LinearLayout summary = card();
        summary.setPadding(dp(18), dp(16), dp(18), dp(16));
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text(urgent > 0 ? "建议优先处理" : "状态良好", 14, urgent > 0 ? AMBER : GREEN_DARK, true), new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(text(urgent + " 款", 14, MUTED, true));
        summary.addView(top);
        TextView copy = text(urgent > 0 ? "有豆子需要留意，先处理香气风险和低库存" : "当前提醒不紧急，可以按喜好安排", 13, MUTED, false);
        copy.setPadding(0, dp(6), 0, 0);
        summary.addView(copy);
        content.addView(summary, fullMargin());

        TextView first = text("需要留意", 14, AMBER, true);
        first.setPadding(0, dp(10), 0, dp(4));
        content.addView(first);
        for (String alert : alerts) {
            content.addView(alertCard(alert), fullMargin());
        }
    }

    private View brewRow(Brew brew) {
        LinearLayout row = card();
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView score = text(String.format(Locale.CHINA, "%.1f", brew.score), 16, GREEN_DARK, true);
        score.setGravity(Gravity.CENTER);
        score.setBackground(round(GREEN_SOFT, dp(12), Color.TRANSPARENT));
        line.addView(score, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, 0, 0);
        copy.addView(text(brew.beanName, 17, INK, true));
        copy.addView(text(String.format(Locale.CHINA, "%s   %.0fg:%03.0fg   %s", brew.method, brew.dose, brew.water, brew.time), 12, MUTED, false));
        line.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(line);
        if (!brew.note.isEmpty()) row.addView(text(brew.note, 13, MUTED, false));
        return row;
    }

    private View brewTimelineList(List<Brew> brews, boolean showBeanName) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(4), 0, dp(2));
        if (brews.isEmpty()) {
            TextView empty = text("还没有冲煮记录", 14, MUTED, true);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(18), dp(16), dp(18));
            empty.setBackground(round(PANEL, dp(14), LINE));
            list.addView(empty);
            return list;
        }
        for (int i = 0; i < brews.size(); i++) {
            list.addView(brewTimelineRow(brews.get(i), showBeanName, i == brews.size() - 1));
        }
        return list;
    }

    private View brewGroupedByDayList(List<Brew> brews, boolean showBeanName) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(4), 0, dp(2));
        if (brews.isEmpty()) {
            TextView empty = text("最近 7 天还没有冲煮记录", 14, MUTED, true);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(18), dp(16), dp(18));
            empty.setBackground(round(PANEL, dp(14), LINE));
            list.addView(empty);
            return list;
        }

        String currentDate = "";
        for (Brew brew : brews) {
            String day = cleanBrewDate(brew.date);
            if (!day.equals(currentDate)) {
                currentDate = day;
                list.addView(brewDayHeader(day, countBrewsOnDate(brews, day)));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 0, 0, dp(5));
            list.addView(brewGroupedTimelineRow(brew, showBeanName), lp);
        }
        return list;
    }

    private View brewDayHeader(String day, int count) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(2), dp(10), dp(2), dp(6));
        header.addView(text(brewDayLabel(day), 14, GREEN_DARK, true), new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(statusBadge(count + " 杯", CREAM, MUTED));
        return header;
    }

    private View brewGroupedTimelineRow(Brew brew, boolean showBeanName) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(3));
        row.setOnClickListener(v -> showEditBrewTimeDialog(brew));

        TextView time = text(cleanText(brew.brewTime, "00:00"), 15, GREEN_DARK, true);
        time.setGravity(Gravity.CENTER);
        time.setPadding(0, dp(8), dp(6), 0);
        row.addView(time, new LinearLayout.LayoutParams(dp(62), -1));

        LinearLayout axis = new LinearLayout(this);
        axis.setOrientation(LinearLayout.VERTICAL);
        axis.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView dot = text("●", 12, GREEN_DARK, true);
        dot.setGravity(Gravity.CENTER);
        dot.setBackground(round(GREEN_SOFT, dp(12), GREEN_DARK));
        axis.addView(dot, new LinearLayout.LayoutParams(dp(27), dp(27)));
        View line = new View(this);
        line.setBackgroundColor(LINE);
        axis.addView(line, new LinearLayout.LayoutParams(dp(2), 0, 1));
        row.addView(axis, new LinearLayout.LayoutParams(dp(34), -1));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), dp(10), dp(12), dp(10));
        copy.setBackground(round(PANEL, dp(12), LINE));
        String title = showBeanName ? brew.beanName : brew.method + " · " + String.format(Locale.CHINA, "%.1f 分", brew.score);
        String detail = String.format(Locale.CHINA, "%s · %.0fg 粉 / %.0fg 水 · %s · %d°C · %s",
                showBeanName ? brew.method : "冲煮",
                brew.dose,
                brew.water,
                cleanText(brew.grind, "研磨未记录"),
                brew.temp,
                cleanText(brew.time, "时间未记录"));
        LinearLayout scoreLine = new LinearLayout(this);
        scoreLine.setGravity(Gravity.CENTER_VERTICAL);
        scoreLine.addView(text(title, 15, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        scoreLine.addView(statusBadge(String.format(Locale.CHINA, "%.1f", brew.score), GREEN_SOFT, GREEN_DARK));
        copy.addView(scoreLine);
        TextView detailView = text(detail, 12, MUTED, false);
        detailView.setPadding(0, dp(3), 0, 0);
        copy.addView(detailView);
        if (!isBlank(brew.note)) {
            TextView note = text(brew.note, 12, GREEN_DARK, false);
            note.setPadding(0, dp(5), 0, 0);
            copy.addView(note);
        }
        View photos = imageGallery(brew.imageUris);
        if (photos != null) copy.addView(photos);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private int countBrewsOnDate(List<Brew> brews, String day) {
        int count = 0;
        for (Brew brew : brews) {
            if (day.equals(cleanBrewDate(brew.date))) count++;
        }
        return count;
    }

    private String brewDayLabel(String raw) {
        LocalDate day = date(raw);
        LocalDate today = LocalDate.now();
        if (day.equals(today)) return "今天 · " + raw;
        if (day.equals(today.minusDays(1))) return "昨天 · " + raw;
        return String.format(Locale.CHINA, "%02d月%02d日 · %s", day.getMonthValue(), day.getDayOfMonth(), raw);
    }

    private View brewTimelineRow(Brew brew, boolean showBeanName, boolean last) {
        String title = showBeanName ? brew.beanName : brew.method + " · " + String.format(Locale.CHINA, "%.1f 分", brew.score);
        String detail = String.format(Locale.CHINA, "%s · %.0fg 粉 / %.0fg 水 · %s · %d°C · %s",
                showBeanName ? brew.method : "冲煮",
                brew.dose,
                brew.water,
                cleanText(brew.grind, "研磨未记录"),
                brew.temp,
                cleanText(brew.time, "时间未记录"));
        View row = timelineRow(brew.dateLabel(), title, detail, brew.note, String.format(Locale.CHINA, "%.1f", brew.score), GREEN_SOFT, GREEN_DARK, last);
        row.setOnClickListener(v -> showEditBrewTimeDialog(brew));
        return row;
    }

    private void showEditBrewTimeDialog(Brew brew) {
        LinearLayout form = form();
        EditText date = input("冲煮日期 yyyy-MM-dd", cleanText(brew.date, LocalDate.now().toString()));
        EditText brewTime = input("冲煮时间 HH:mm", cleanText(brew.brewTime, LocalTime.now().format(BREW_TIME_FORMAT)));
        List<Bean> choices = db.availableBatches();
        Bean current = db.beanById(brew.beanId);
        final Bean[] picked = {current};
        TextView beanPick = choiceField(current == null ? brew.beanName : current.name + " · 剩余 " + fmt(current.remainingGram) + "g");
        beanPick.setOnClickListener(v -> {
            String[] names = new String[choices.size()];
            for (int i = 0; i < choices.size(); i++) names[i] = choices.get(i).name + " · " + choices.get(i).roaster + " · 剩余 " + fmt(choices.get(i).remainingGram) + "g";
            new AlertDialog.Builder(this).setTitle("选择实际使用的豆子").setItems(names, (d, which) -> {
                picked[0] = choices.get(which);
                beanPick.setText(names[which]);
            }).show();
        });
        addAll(form,
                formSection("所用豆子", labeled("豆子", beanPick)),
                formSection("记录时间",
                        labeled("日期", date),
                        labeled("时间", brewTime)));

        new AlertDialog.Builder(this)
                .setTitle("修改冲煮记录")
                .setView(scrollForm(form))
                .setPositiveButton("保存", (d, w) -> {
                    if (picked[0] == null) return;
                    if (picked[0].id != brew.beanId && picked[0].remainingGram + 0.001 < brew.dose) {
                        Toast.makeText(this, "新选择的豆子余量不足 " + fmt(brew.dose) + "g", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    db.updateBrewDateTime(brew.id, date.getText().toString(), brewTime.getText().toString());
                    db.moveBrewToBean(brew.id, brew.beanId, picked[0].id, brew.dose);
                    Toast.makeText(this, "冲煮记录与库存已更新", Toast.LENGTH_SHORT).show();
                    render();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private View batchTimeline(Bean bean) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        List<Bean> batches = bean.batchesOrSelf();
        for (int i = 0; i < batches.size(); i++) {
            Bean batch = batches.get(i);
            String batchName = "第 " + (i + 1) + " 批";
            String detail = String.format(Locale.CHINA, "剩余 %s / %sg · %s · %s",
                    fmt(batch.remainingGram),
                    fmt(batch.totalGram),
                    gramPriceText(batch.totalGram, batch.price),
                    openState(batch));
            list.addView(timelineRow(batch.purchaseDate, batchName + " 入库", detail, "烘焙 " + batch.roastDate, "入", CREAM, GREEN_DARK, false));
            list.addView(timelineRow(batch.roastDate, batchName + " 烘焙", batch.roastLevel + " · 赏味至 " + cleanBestBefore(batch), "", "烘", AMBER_SOFT, AMBER, false));
            if (!isBlank(batch.openDate)) {
                list.addView(timelineRow(batch.openDate, batchName + " 开封", "开始进入日常消耗", "", "开", GREEN_SOFT, GREEN_DARK, false));
            }
            list.addView(timelineRow(cleanBestBefore(batch), batchName + " 赏味期", drinkingWindow(batch) + " · 剩余约 " + Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), date(cleanBestBefore(batch)))) + " 天", "", "赏", ROSE_SOFT, ROSE, i == batches.size() - 1));
        }
        return list;
    }

    private View timelineRow(String date, String title, String detail, String note, String marker, int markerFill, int markerInk, boolean last) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 0, 0, last ? 0 : dp(10));

        TextView dateView = text(dateLabel(date), 11, MUTED, true);
        dateView.setGravity(Gravity.RIGHT | Gravity.TOP);
        dateView.setPadding(0, dp(8), dp(6), 0);
        row.addView(dateView, new LinearLayout.LayoutParams(dp(68), -1));

        LinearLayout axis = new LinearLayout(this);
        axis.setOrientation(LinearLayout.VERTICAL);
        axis.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView dot = text(marker, 11, markerInk, true);
        dot.setGravity(Gravity.CENTER);
        dot.setBackground(round(markerFill, dp(12), markerInk));
        axis.addView(dot, new LinearLayout.LayoutParams(dp(28), dp(28)));
        View line = new View(this);
        line.setBackgroundColor(last ? Color.TRANSPARENT : LINE);
        axis.addView(line, new LinearLayout.LayoutParams(dp(2), 0, 1));
        row.addView(axis, new LinearLayout.LayoutParams(dp(34), -1));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(10), dp(12), dp(10));
        body.setBackground(round(PANEL, dp(12), LINE));
        body.addView(text(title, 15, INK, true));
        TextView detailView = text(detail, 12, MUTED, false);
        detailView.setPadding(0, dp(4), 0, 0);
        body.addView(detailView);
        if (!isBlank(note)) {
            TextView noteView = text(note, 12, GREEN_DARK, false);
            noteView.setPadding(0, dp(7), 0, 0);
            body.addView(noteView);
        }
        row.addView(body, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private String dateLabel(String raw) {
        LocalDate d = date(raw);
        String label = String.format(Locale.CHINA, "%02d/%02d\n%d", d.getMonthValue(), d.getDayOfMonth(), d.getYear());
        if (raw != null && raw.trim().length() >= 16) label += "\n" + raw.trim().substring(11, 16);
        return label;
    }

    private String cleanText(String raw, String fallback) {
        return isBlank(raw) ? fallback : raw.trim();
    }

    private View todaySmallCard(Bean bean) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(round(PANEL, dp(14), LINE));
        card.addView(text(bean.name, 16, INK, true));
        card.addView(statusBadge(drinkingWindow(bean), GREEN_SOFT, GREEN_DARK));
        TextView cups = text("还可冲 " + bestRecipeCups(bean) + " 杯", 12, MUTED, false);
        cups.setPadding(0, dp(6), 0, 0);
        card.addView(cups);
        card.setOnClickListener(v -> openBrew(drinkBatch(bean)));
        return card;
    }

    private void openBrew(Bean bean) {
        if (bean != null) brewBeanId = bean.id;
        tab = "brew";
        render();
    }

    private View brewBeanPicker(Bean bean) {
        LinearLayout pick = new LinearLayout(this);
        pick.setOrientation(LinearLayout.HORIZONTAL);
        pick.setGravity(Gravity.CENTER_VERTICAL);
        pick.setPadding(dp(14), dp(12), dp(14), dp(12));
        pick.setBackground(round(PANEL, dp(18), LINE));
        pick.setElevation(0);

        TextView avatar = text(bean.name.substring(0, Math.min(1, bean.name.length())), 18, Color.WHITE, true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(round(accentFor(bean.name), dp(15), Color.TRANSPARENT));
        pick.addView(avatar, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout beanCopy = new LinearLayout(this);
        beanCopy.setOrientation(LinearLayout.VERTICAL);
        beanCopy.setPadding(dp(12), 0, 0, 0);
        beanCopy.addView(text(bean.name, 18, INK, true));
        beanCopy.addView(text("第 " + batchOrdinal(bean) + " 批 · 剩余 " + fmt(bean.remainingGram) + " / " + fmt(bean.totalGram) + " g", 12, MUTED, true));
        pick.addView(beanCopy, new LinearLayout.LayoutParams(0, -2, 1));

        TextView change = statusBadge("更换", CREAM, GREEN_DARK);
        change.setClickable(true);
        change.setFocusable(true);
        change.setOnClickListener(v -> showBrewBeanPicker());
        pick.addView(change);
        pick.setOnClickListener(v -> showBrewBeanPicker());
        return pick;
    }

    private void showBrewBeanPicker() {
        List<Bean> batches = db.availableBatches();
        if (batches.isEmpty()) {
            Toast.makeText(this, "没有可用库存", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[batches.size()];
        for (int i = 0; i < batches.size(); i++) {
            Bean b = batches.get(i);
            labels[i] = b.name + " · 剩余 " + fmt(b.remainingGram) + "/" + fmt(b.totalGram) + "g";
        }
        new AlertDialog.Builder(this)
                .setTitle("更换咖啡豆")
                .setItems(labels, (dialog, which) -> openBrew(batches.get(which)))
                .show();
    }

    private LinearLayout segmented(String field, String[] options, BrewInputs inputs, Runnable onEspressoSelected) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(4), dp(4), dp(4), dp(4));
        row.setBackground(round(SAND, dp(12), Color.TRANSPARENT));
        List<TextView> views = new ArrayList<>();
        for (String option : options) {
            TextView chip = text(option, 14, option.equals(inputs.method) ? GREEN_DARK : MUTED, true);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(dp(48));
            chip.setBackground(round(option.equals(inputs.method) ? CREAM : Color.TRANSPARENT, dp(10), option.equals(inputs.method) ? LINE : Color.TRANSPARENT));
            chip.setOnClickListener(v -> {
                inputs.method = option;
                if ("意式".equals(option) && onEspressoSelected != null) onEspressoSelected.run();
                for (TextView item : views) {
                    boolean active = item.getText().toString().equals(inputs.method);
                    item.setTextColor(active ? GREEN_DARK : MUTED);
                    item.setBackground(round(active ? CREAM : Color.TRANSPARENT, dp(10), active ? LINE : Color.TRANSPARENT));
                }
            });
            views.add(chip);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
            row.addView(chip, lp);
        }
        return row;
    }

    private LinearLayout chipGroup(String field, String[] options, BrewInputs inputs, boolean single) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(single ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        wrap.setPadding(0, dp(4), 0, dp(8));
        List<TextView> views = new ArrayList<>();
        LinearLayout row = single ? wrap : null;
        for (int i = 0; i < options.length; i++) {
            String option = options[i];
            if (!single && i % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                wrap.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            boolean active = single ? option.equals(inputs.grind) : inputs.noteTags.contains(option);
            TextView chip = choiceChip(option, active);
            chip.setOnClickListener(v -> {
                if (single) {
                    inputs.grind = option;
                    for (TextView item : views) setChoiceChipActive(item, item.getText().toString().equals(option));
                } else {
                    if (inputs.noteTags.contains(option)) inputs.noteTags.remove(option);
                    else inputs.noteTags.add(option);
                    setChoiceChipActive(chip, inputs.noteTags.contains(option));
                }
            });
            views.add(chip);
            LinearLayout.LayoutParams lp = single ? new LinearLayout.LayoutParams(-2, dp(42)) : new LinearLayout.LayoutParams(0, dp(42), 1);
            lp.setMargins(0, dp(4), dp(8), dp(4));
            row.addView(chip, lp);
        }
        return wrap;
    }

    private TextView choiceChip(String label, boolean active) {
        TextView chip = text(label, 14, active ? GREEN_DARK : MUTED, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14), 0, dp(14), 0);
        chip.setBackground(round(active ? GREEN_SOFT : Color.TRANSPARENT, dp(20), active ? Color.rgb(118, 181, 165) : LINE));
        chip.setClickable(true);
        chip.setFocusable(true);
        return chip;
    }

    private void setChoiceChipActive(TextView chip, boolean active) {
        chip.setTextColor(active ? GREEN_DARK : MUTED);
        chip.setBackground(round(active ? GREEN_SOFT : Color.TRANSPARENT, dp(20), active ? Color.rgb(118, 181, 165) : LINE));
    }

    private LinearLayout ratingSection(BrewInputs inputs) {
        LinearLayout section = formSection("这杯怎么样");
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text("这杯怎么样", 13, GREEN_DARK, true);
        top.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        TextView score = text("", 32, INK, true);
        top.addView(score);
        TextView max = text(" / 5", 15, MUTED, true);
        top.addView(max);
        section.addView(top);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView minus = flatCircleButton("-", MUTED, CREAM, LINE);
        SeekBar bar = new SeekBar(this);
        bar.setMax(50);
        bar.setProgress(inputs.score);
        TextView plus = flatCircleButton("+", MUTED, CREAM, LINE);
        Runnable refresh = () -> score.setText(String.format(Locale.CHINA, "%.1f", inputs.score / 10.0));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                inputs.score = Math.max(0, progress);
                refresh.run();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        minus.setOnClickListener(v -> {
            inputs.score = Math.max(0, inputs.score - 1);
            bar.setProgress(inputs.score);
            refresh.run();
        });
        plus.setOnClickListener(v -> {
            inputs.score = Math.min(50, inputs.score + 1);
            bar.setProgress(inputs.score);
            refresh.run();
        });
        refresh.run();

        row.addView(minus, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(0, dp(48), 1);
        barLp.setMargins(dp(10), 0, dp(10), 0);
        row.addView(bar, barLp);
        row.addView(plus, new LinearLayout.LayoutParams(dp(40), dp(40)));
        section.addView(row);
        return section;
    }

    private LinearLayout twoColumns(View left, View right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, -2, 1);
        leftLp.setMargins(0, 0, dp(7), 0);
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, -2, 1);
        rightLp.setMargins(dp(7), 0, 0, 0);
        row.addView(left, leftLp);
        row.addView(right, rightLp);
        return row;
    }

    private LinearLayout withSuffix(EditText input, String suffix) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackground(round(BG, dp(12), LINE));
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(dp(12), 0, 0, 0);
        box.addView(input, new LinearLayout.LayoutParams(0, dp(52), 1));
        TextView s = text(suffix, 14, MUTED, true);
        s.setGravity(Gravity.CENTER);
        box.addView(s, new LinearLayout.LayoutParams(dp(48), dp(52)));
        return box;
    }

    private TextView compactRatioField(EditText dose, EditText water) {
        TextView out = text("", 17, INK, true);
        out.setGravity(Gravity.CENTER_VERTICAL);
        out.setPadding(dp(14), 0, dp(14), 0);
        out.setMinHeight(dp(48));
        out.setBackground(round(CREAM, dp(10), Color.TRANSPARENT));
        Runnable refresh = () -> {
            double d = num(dose, 0);
            double w = num(water, 0);
            out.setText(d <= 0 || w <= 0 ? "粉水比                                      -" : String.format(Locale.CHINA, "粉水比                                      1 : %.1f", w / d));
        };
        TextWatcher watcher = simpleWatcher(refresh);
        dose.addTextChangedListener(watcher);
        water.addTextChangedListener(watcher);
        refresh.run();
        return out;
    }

    private TextWatcher simpleWatcher(Runnable onChange) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                onChange.run();
            }
            @Override public void afterTextChanged(Editable s) {}
        };
    }

    private String brewNote(List<String> tags, String extra) {
        StringBuilder out = new StringBuilder();
        for (String tag : tags) {
            if (out.length() > 0) out.append("，");
            out.append(tag);
        }
        if (!isBlank(extra)) {
            if (out.length() > 0) out.append("，");
            out.append(extra.trim());
        }
        return out.toString();
    }

    private String batchOrdinal(Bean bean) {
        List<Bean> all = db.sameGroupBatches(bean);
        for (int i = 0; i < all.size(); i++) if (all.get(i).id == bean.id) return String.valueOf(i + 1);
        return "1";
    }

    private View stockConsumptionCard() {
        double total = db.totalConsumedFromStock();
        double week = db.consumptionSince(LocalDate.now().minusDays(6));
        double month = db.consumptionSince(LocalDate.now().minusDays(29));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.setBackground(round(PANEL, dp(16), LINE));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text("库存消耗量", 17, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(statusBadge("总量按库存差", CREAM, MUTED));
        box.addView(top);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setPadding(0, dp(10), 0, 0);
        grid.addView(compactConsumption("总消耗", fmt(total) + "g", GREEN_SOFT, GREEN_DARK));
        grid.addView(compactConsumption("近一周", fmt(week) + "g", AMBER_SOFT, AMBER));
        grid.addView(compactConsumption("近一月", fmt(month) + "g", ROSE_SOFT, ROSE));
        box.addView(grid);
        return box;
    }

    private View compactConsumption(String label, String value, int fill, int ink) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(9), dp(8), dp(9));
        card.setBackground(round(fill, dp(12), Color.TRANSPARENT));
        TextView v = text(value, 18, ink, true);
        v.setGravity(Gravity.CENTER);
        TextView l = text(label, 11, MUTED, false);
        l.setGravity(Gravity.CENTER);
        card.addView(v);
        card.addView(l);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = (getResources().getDisplayMetrics().widthPixels - dp(68)) / 3;
        lp.setMargins(dp(3), dp(4), dp(3), dp(4));
        card.setLayoutParams(lp);
        return card;
    }

    private View barChartCard(String title, String sub, List<ChartItem> items, String unit) {
        LinearLayout box = card();
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.addView(text(title, 17, INK, true));
        TextView subtitle = text(sub, 12, MUTED, false);
        subtitle.setPadding(0, dp(2), 0, dp(10));
        box.addView(subtitle);
        double max = 0;
        for (ChartItem item : items) max = Math.max(max, item.value);
        if (items.isEmpty() || max <= 0) {
            box.addView(text("暂无足够数据", 13, MUTED, true));
            return box;
        }
        for (ChartItem item : items) {
            box.addView(barChartRow(item.label, item.value, max, unit));
        }
        return box;
    }

    private View distributionChartCard(String title, String sub, List<ChartItem> items, String unit) {
        return items.size() <= 4 ? pieChartCard(title, sub, items, unit) : barChartCard(title, sub, items, unit);
    }

    private View pieChartCard(String title, String sub, List<ChartItem> items, String unit) {
        LinearLayout box = card();
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.addView(text(title, 17, INK, true));
        TextView subtitle = text(sub, 12, MUTED, false);
        subtitle.setPadding(0, dp(2), 0, dp(8));
        box.addView(subtitle);
        double total = 0;
        for (ChartItem item : items) total += item.value;
        if (items.isEmpty() || total <= 0) {
            box.addView(text("暂无足够数据", 13, MUTED, true));
            return box;
        }
        LinearLayout body = new LinearLayout(this);
        body.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(new PieChartView(this, items), new LinearLayout.LayoutParams(dp(150), dp(150)));
        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.VERTICAL);
        legend.setGravity(Gravity.CENTER_VERTICAL);
        legend.setPadding(dp(10), 0, 0, 0);
        int[] colors = pieColors();
        for (int i = 0; i < items.size(); i++) {
            ChartItem item = items.get(i);
            TextView entry = text("●  " + item.label + "  " + formatChartValue(item.value) + unit, 12, colors[i % colors.length], true);
            entry.setMaxLines(2);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, i == 0 ? 0 : dp(8), 0, 0);
            legend.addView(entry, lp);
        }
        body.addView(legend, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(body);
        return box;
    }

    private int[] pieColors() {
        return new int[]{GREEN, AMBER, ROSE, Color.rgb(128, 164, 223)};
    }

    private View barChartRow(String label, double value, double max, String unit) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(5), 0, dp(5));

        TextView labelView = text(label, 12, MUTED, true);
        labelView.setMaxLines(4);
        row.addView(labelView, new LinearLayout.LayoutParams(dp(138), -2));

        LinearLayout track = new LinearLayout(this);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setGravity(Gravity.CENTER_VERTICAL);
        track.setBackground(round(CREAM, dp(10), Color.TRANSPARENT));
        View fill = new View(this);
        fill.setBackground(round(GREEN_SOFT, dp(10), Color.TRANSPARENT));
        int fillWidth = Math.max(dp(12), (int) ((getResources().getDisplayMetrics().widthPixels - dp(252)) * (value / max)));
        track.addView(fill, new LinearLayout.LayoutParams(fillWidth, dp(12)));
        row.addView(track, new LinearLayout.LayoutParams(0, dp(14), 1));

        TextView valueView = text(formatChartValue(value) + unit, 12, INK, true);
        valueView.setGravity(Gravity.RIGHT);
        row.addView(valueView, new LinearLayout.LayoutParams(dp(62), -2));
        return row;
    }

    private View columnChartCard(String title, String subtitleText, List<ChartItem> items, String unit) {
        LinearLayout box = card();
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.addView(text(title, 17, INK, true));
        TextView subtitle = text(subtitleText, 12, MUTED, false);
        subtitle.setPadding(0, dp(2), 0, dp(10));
        box.addView(subtitle);
        double max = 0;
        for (ChartItem item : items) max = Math.max(max, item.value);

        LinearLayout chart = new LinearLayout(this);
        chart.setOrientation(LinearLayout.HORIZONTAL);
        chart.setGravity(Gravity.BOTTOM);
        chart.setPadding(0, dp(8), 0, 0);

        LinearLayout yAxis = new LinearLayout(this);
        yAxis.setOrientation(LinearLayout.VERTICAL);
        yAxis.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView top = text(max > 0 ? formatChartValue(max) : "0", 10, MUTED, true);
        top.setGravity(Gravity.CENTER);
        yAxis.addView(top, new LinearLayout.LayoutParams(-1, dp(20)));
        View axis = new View(this);
        axis.setBackgroundColor(LINE);
        yAxis.addView(axis, new LinearLayout.LayoutParams(dp(1), dp(86)));
        TextView zero = text("0", 10, MUTED, true);
        zero.setGravity(Gravity.CENTER);
        yAxis.addView(zero, new LinearLayout.LayoutParams(-1, dp(18)));
        chart.addView(yAxis, new LinearLayout.LayoutParams(dp(28), -2));

        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.setGravity(Gravity.BOTTOM);
        columns.setBackground(round(Color.argb(30, 255, 255, 255), dp(12), LINE));
        columns.setPadding(dp(6), dp(7), dp(6), dp(6));
        for (ChartItem item : items) columns.addView(timeColumn(item, max), new LinearLayout.LayoutParams(0, -2, 1));
        chart.addView(columns, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(chart);
        return box;
    }

    private View regionMapCard(List<ChartItem> items) {
        LinearLayout box = card();
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.addView(text("产区分布", 17, INK, true));
        TextView subtitle = text("地图上的圆点代表当前库存豆子的主要产区，圆点越大数量越多", 12, MUTED, false);
        subtitle.setPadding(0, dp(2), 0, dp(10));
        box.addView(subtitle);
        if (items.isEmpty()) {
            box.addView(text("暂无产区数据", 13, MUTED, true));
            return box;
        }
        FrameLayout mapBox = new FrameLayout(this);
        mapBox.setBackground(round(Color.argb(34, 255, 255, 255), dp(14), Color.TRANSPARENT));
        ImageView mapBase = new ImageView(this);
        mapBase.setImageResource(getResources().getIdentifier("world_map_base", "drawable", getPackageName()));
        mapBase.setScaleType(ImageView.ScaleType.FIT_XY);
        mapBase.setAlpha(0.52f);
        mapBase.setColorFilter(Color.rgb(238, 230, 212));
        mapBox.addView(mapBase, new FrameLayout.LayoutParams(-1, -1));
        mapBox.addView(new WorldMapView(this, items), new FrameLayout.LayoutParams(-1, -1));
        box.addView(mapBox, new LinearLayout.LayoutParams(-1, dp(198)));
        GridLayout legend = new GridLayout(this);
        legend.setColumnCount(2);
        legend.setPadding(0, dp(10), 0, 0);
        for (int i = 0; i < items.size(); i++) {
            ChartItem item = items.get(i);
            TextView entry = text("●  " + item.label + "  " + formatChartValue(item.value) + "款", 11, i % 2 == 0 ? GREEN_DARK : AMBER, true);
            entry.setMaxLines(2);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = (getResources().getDisplayMetrics().widthPixels - dp(76)) / 2;
            lp.setMargins(i % 2 == 0 ? 0 : dp(8), dp(3), 0, dp(3));
            legend.addView(entry, lp);
        }
        box.addView(legend);
        return box;
    }

    private View timeColumn(ChartItem item, double max) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        cell.setPadding(dp(2), 0, dp(2), 0);

        TextView value = text(formatChartValue(item.value), 10, INK, true);
        value.setGravity(Gravity.CENTER);
        cell.addView(value, new LinearLayout.LayoutParams(-1, dp(18)));

        int barHeight = max <= 0 || item.value <= 0 ? dp(5) : Math.max(dp(10), (int) (dp(78) * item.value / max));
        LinearLayout barSlot = new LinearLayout(this);
        barSlot.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        View bar = new View(this);
        bar.setBackground(round(item.value > 0 ? GREEN_SOFT : CREAM, dp(6), Color.TRANSPARENT));
        barSlot.addView(bar, new LinearLayout.LayoutParams(dp(18), barHeight));
        cell.addView(barSlot, new LinearLayout.LayoutParams(-1, dp(82)));

        View tick = new View(this);
        tick.setBackgroundColor(LINE);
        cell.addView(tick, new LinearLayout.LayoutParams(dp(1), dp(6)));

        TextView label = text(item.label, 9, MUTED, true);
        label.setGravity(Gravity.CENTER);
        cell.addView(label, new LinearLayout.LayoutParams(-1, dp(24)));
        return cell;
    }

    private String formatChartValue(double value) {
        if (Math.abs(value - Math.round(value)) < 0.05) return String.valueOf((int) Math.round(value));
        return String.format(Locale.CHINA, "%.1f", value);
    }

    private View alertCard(String alert) {
        boolean urgent = alert.contains("低库存") || alert.contains("过赏味") || alert.contains("超过");
        LinearLayout row = card();
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = text(urgent ? "!" : "✓", 16, urgent ? ROSE : GREEN_DARK, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(round(urgent ? ROSE_SOFT : GREEN_SOFT, dp(12), Color.TRANSPARENT));
        line.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));
        TextView copy = text(alert, 15, INK, true);
        copy.setPadding(dp(12), 0, 0, 0);
        line.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(line);
        return row;
    }

    private View emptyAlertCard() {
        LinearLayout box = card();
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(16), dp(20), dp(16), dp(20));
        box.addView(statusBadge("✓ 其他豆子状态良好", CREAM, GREEN_DARK));
        return box;
    }

    private View statCard(String label, String value) {
        LinearLayout card = card();
        card.setMinimumWidth(dp(150));
        card.addView(text(value, 25, INK, true));
        card.addView(text(label, 13, MUTED, false));
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = getResources().getDisplayMetrics().widthPixels / 2 - dp(28);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        card.setLayoutParams(lp);
        return card;
    }

    private void addSoftMetric(LinearLayout parent, String value, String label, int fill, int ink) {
        LinearLayout m = new LinearLayout(this);
        m.setOrientation(LinearLayout.VERTICAL);
        m.setPadding(dp(12), dp(10), dp(12), dp(10));
        m.setBackground(round(fill, dp(12), Color.TRANSPARENT));
        m.addView(text(value, 20, ink, true));
        m.addView(text(label, 12, Color.argb(205, 255, 255, 255), true));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(0, 0, dp(8), 0);
        parent.addView(m, lp);
    }

    private View compactStat(String label, String value, int fill, int ink, int stroke) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(round(fill, dp(12), stroke));
        card.addView(text(value, 20, ink, true));
        card.addView(text(label, 12, MUTED, false));
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = getResources().getDisplayMetrics().widthPixels / 2 - dp(34);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        card.setLayoutParams(lp);
        return card;
    }

    private TextView legendDot(String label, int color) {
        TextView v = text("● " + label, 11, color, false);
        v.setPadding(0, 0, dp(10), 0);
        return v;
    }

    private TextView statusBadge(String label, int fill, int ink) {
        TextView v = text(label, 11, ink, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(8), dp(3), dp(8), dp(3));
        v.setBackground(round(fill, dp(12), Color.argb(90, 220, 207, 185)));
        return v;
    }

    private ImageUploadField imageUploadField(String imageUris) {
        ImageUploadField field = new ImageUploadField();
        field.images.addAll(imageList(imageUris));
        field.box = new LinearLayout(this);
        field.box.setOrientation(LinearLayout.VERTICAL);

        field.preview = new LinearLayout(this);
        field.preview.setOrientation(LinearLayout.HORIZONTAL);
        field.preview.setPadding(0, dp(6), 0, dp(8));
        field.box.addView(field.preview, new LinearLayout.LayoutParams(-1, -2));

        Button pick = subtleButton("选择图片（1-3张）");
        pick.setTextSize(13);
        pick.setOnClickListener(v -> {
            pendingImageField = field;
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, PICK_BEAN_IMAGES);
        });
        field.box.addView(pick, new LinearLayout.LayoutParams(-1, dp(44)));

        TextView clear = text("清空图片", 12, ROSE, true);
        clear.setGravity(Gravity.CENTER);
        clear.setPadding(0, dp(8), 0, 0);
        clear.setOnClickListener(v -> field.setImages(new ArrayList<>()));
        field.box.addView(clear, new LinearLayout.LayoutParams(-1, -2));
        field.refresh();
        return field;
    }

    private List<String> imageList(String imageUris) {
        List<String> out = new ArrayList<>();
        if (isBlank(imageUris)) return out;
        String[] parts = imageUris.split("\\n");
        for (String part : parts) {
            String uri = part.trim();
            if (!uri.isEmpty() && !out.contains(uri) && out.size() < 3) out.add(uri);
        }
        return out;
    }

    private String joinImages(List<String> images) {
        StringBuilder out = new StringBuilder();
        for (String image : images) {
            if (isBlank(image)) continue;
            if (out.length() > 0) out.append("\n");
            out.append(image.trim());
            if (out.length() > 0 && out.toString().split("\\n").length >= 3) break;
        }
        return out.toString();
    }

    private boolean isConsumed(Bean bean) {
        return bean == null || bean.remainingGram <= 0;
    }

    class ImageUploadField {
        LinearLayout box;
        LinearLayout preview;
        List<String> images = new ArrayList<>();

        void setImages(List<String> values) {
            images.clear();
            for (String value : values) {
                if (!isBlank(value) && images.size() < 3) images.add(value.trim());
            }
            refresh();
        }

        void appendImages(List<String> values) {
            for (String value : values) {
                if (isBlank(value) || images.size() >= 3) continue;
                String uri = value.trim();
                if (!images.contains(uri)) images.add(uri);
            }
            refresh();
        }

        void refresh() {
            preview.removeAllViews();
            if (images.isEmpty()) {
                TextView empty = text("未上传图片", 12, MUTED, true);
                empty.setGravity(Gravity.CENTER);
                empty.setBackground(round(CREAM, dp(10), LINE));
                preview.addView(empty, new LinearLayout.LayoutParams(-1, dp(48)));
                return;
            }
            for (int i = 0; i < images.size(); i++) {
                final int index = i;
                FrameLayout tile = new FrameLayout(MainActivity.this);
                tile.setBackground(round(PANEL, dp(10), LINE));
                ImageView image = new ImageView(MainActivity.this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                setCachedImage(image, images.get(i), dp(72));
                tile.addView(image, new FrameLayout.LayoutParams(-1, -1));
                image.setOnClickListener(v -> showImagePreview(images.get(index)));
                TextView remove = text("×", 15, Color.WHITE, true);
                remove.setGravity(Gravity.CENTER);
                remove.setBackground(round(Color.argb(205, 178, 73, 83), dp(12), Color.TRANSPARENT));
                FrameLayout.LayoutParams removeLp = new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.RIGHT | Gravity.TOP);
                removeLp.setMargins(0, dp(4), dp(4), 0);
                tile.addView(remove, removeLp);
                remove.setOnClickListener(v -> {
                    images.remove(index);
                    refresh();
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(72), 1);
                lp.setMargins(i == 0 ? 0 : dp(7), 0, 0, 0);
                preview.addView(tile, lp);
            }
        }
    }

    private void showAddBeanDialog() {
        showBeanDialog(null);
    }

    private void showEditBeanDialog(Bean bean) {
        showBeanBaseDialog(bean);
    }

    private void showBeanDialog(Bean bean) {
        LinearLayout form = form();
        boolean editing = bean != null;
        EditText name = input("豆子名称", editing ? bean.name : "花蝴蝶");
        EditText roaster = input("烘焙商", editing ? bean.roaster : "Fisher Coffee");
        final String[] selectedBeanTypes = {normalizeBeanTypes(editing ? bean.beanType : DEFAULT_BEAN_TYPE)};
        TextView beanType = choiceField(selectedBeanTypes[0]);
        beanType.setOnClickListener(v -> showBeanTypeDialog(beanType, selectedBeanTypes));
        final boolean[] isBlend = {editing && bean.isBlend};
        TextView productType = choiceField(isBlend[0] ? "拼配" : "单品");
        productType.setOnClickListener(v -> showProductTypeDialog(productType, isBlend));
        EditText origin = input("产地", editing ? bean.origin : "埃塞俄比亚");
        EditText blendDetails = input("拼配构成（可选）", editing ? bean.blendDetails : "");
        EditText process = input("处理法", editing ? bean.process : "日晒");
        EditText roast = input("烘焙度", editing ? bean.roastLevel : "浅烘");
        EditText tags = input("风味标签", editing ? bean.flavorTags : "莓果,花香,柑橘");
        ImageUploadField packageImages = imageUploadField(editing ? bean.packageImageUris : "");
        ImageUploadField beanImages = imageUploadField(editing ? bean.beanImageUris : "");
        EditText total = numberInput("规格克数", editing ? fmt(bean.totalGram) : "200");
        EditText remaining = numberInput("剩余克数", editing ? fmt(bean.remainingGram) : "200");
        EditText price = numberInput("购买价", editing ? fmt(bean.price) : "88");
        TextView gramPrice = gramPriceField(total, price);
        EditText purchaseDate = input("购买日期 yyyy-MM-dd", editing ? bean.purchaseDate : LocalDate.now().toString());
        EditText roastDate = input("烘焙日期 yyyy-MM-dd", editing ? bean.roastDate : LocalDate.now().minusDays(7).toString());
        EditText openDate = input("开封日期 yyyy-MM-dd（留空=未拆封）", editing ? bean.openDate : "");
        EditText bestBeforeDate = bestBeforeInput(roastDate, editing ? bean.bestBeforeDate : "");
        addAll(form,
                formSection("基本信息",
                        labeled("豆子名称", name),
                        labeled("烘焙商", roaster),
                        labeled("产品类型", productType),
                        labeled("豆种", beanType)),
                formSection("产地与风味",
                        labeled("产地", origin),
                        labeled("拼配构成（可选）", blendDetails),
                        labeled("处理法", process),
                        labeled("烘焙度", roast),
                        labeled("风味标签", tags)),
                formSection("豆子图片",
                        labeled("包装图片", packageImages.box),
                        labeled("豆子外观", beanImages.box)),
                formSection("规格与库存",
                        labeled("规格克数", total),
                        labeled("剩余克数", remaining),
                        labeled("购买价", price),
                        labeled("克重价格", gramPrice)),
                formSection("日期",
                        labeled("购买日期", purchaseDate),
                        labeled("烘焙日期", roastDate),
                        labeled("开封日期", openDate),
                        labeled("赏味期限", bestBeforeDate)));

        new AlertDialog.Builder(this)
                .setTitle(editing ? "编辑豆子" : "新增豆子")
                .setView(scrollForm(form))
                .setPositiveButton("保存", (d, w) -> {
                    Bean updated = new Bean();
                    updated.id = editing ? bean.id : -1;
                    updated.name = name.getText().toString();
                    updated.roaster = roaster.getText().toString();
                    updated.beanType = selectedBeanTypes[0];
                    updated.isBlend = isBlend[0];
                    updated.origin = origin.getText().toString();
                    updated.blendDetails = blendDetails.getText().toString();
                    updated.process = process.getText().toString();
                    updated.roastLevel = roast.getText().toString();
                    updated.flavorTags = tags.getText().toString();
                    updated.packageImageUris = joinImages(packageImages.images);
                    updated.beanImageUris = joinImages(beanImages.images);
                    updated.imageUris = updated.packageImageUris;
                    updated.totalGram = num(total, 200);
                    updated.remainingGram = num(remaining, updated.totalGram);
                    updated.price = num(price, 0);
                    updated.purchaseDate = purchaseDate.getText().toString();
                    updated.roastDate = roastDate.getText().toString();
                    updated.openDate = openDate.getText().toString();
                    updated.bestBeforeDate = bestBeforeDate.getText().toString();
                    if (editing) {
                        db.updateBean(updated);
                        Toast.makeText(this, "豆子已更新", Toast.LENGTH_SHORT).show();
                    } else {
                        db.addBean(updated);
                        Toast.makeText(this, "豆子已新增", Toast.LENGTH_SHORT).show();
                    }
                    render();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showBeanBaseDialog(Bean bean) {
        LinearLayout form = form();
        EditText name = input("豆子名称", bean.name);
        EditText roaster = input("烘焙商", bean.roaster);
        final String[] selectedBeanTypes = {normalizeBeanTypes(bean.beanType)};
        TextView beanType = choiceField(selectedBeanTypes[0]);
        beanType.setOnClickListener(v -> showBeanTypeDialog(beanType, selectedBeanTypes));
        final boolean[] isBlend = {bean.isBlend};
        TextView productType = choiceField(isBlend[0] ? "拼配" : "单品");
        productType.setOnClickListener(v -> showProductTypeDialog(productType, isBlend));
        EditText origin = input("产地", bean.origin);
        EditText blendDetails = input("拼配构成（可选）", bean.blendDetails);
        EditText process = input("处理法", bean.process);
        EditText roast = input("烘焙度", bean.roastLevel);
        EditText tags = input("风味标签", bean.flavorTags);
        ImageUploadField packageImages = imageUploadField(bean.packageImageUris);
        ImageUploadField beanImages = imageUploadField(bean.beanImageUris);
        addAll(form,
                formSection("基本信息",
                        labeled("豆子名称", name),
                        labeled("烘焙商", roaster),
                        labeled("产品类型", productType),
                        labeled("豆种", beanType)),
                formSection("产地与风味",
                        labeled("产地", origin),
                        labeled("拼配构成（可选）", blendDetails),
                        labeled("处理法", process),
                        labeled("烘焙度", roast),
                        labeled("风味标签", tags)),
                formSection("豆子图片",
                        labeled("包装图片", packageImages.box),
                        labeled("豆子外观", beanImages.box)));

        List<Bean> batches = bean.batchesOrSelf();
        List<EditText> totals = new ArrayList<>();
        List<EditText> remainings = new ArrayList<>();
        List<EditText> prices = new ArrayList<>();
        List<EditText> purchaseDates = new ArrayList<>();
        List<EditText> roastDates = new ArrayList<>();
        List<EditText> openDates = new ArrayList<>();
        List<EditText> bestBeforeDates = new ArrayList<>();
        for (int i = 0; i < batches.size(); i++) {
            Bean batch = batches.get(i);
            LinearLayout batchBox = batchEditor(i + 1, batch, totals, remainings, prices, purchaseDates, roastDates, openDates, bestBeforeDates);
            form.addView(batchBox, new LinearLayout.LayoutParams(-1, -2));
        }

        new AlertDialog.Builder(this)
                .setTitle("编辑豆子与批次")
                .setMessage("基础信息会同步到所有批次，规格、库存、价格和时间按批次分别保存。")
                .setView(scrollForm(form))
                .setPositiveButton("保存", (d, w) -> {
                    for (int i = 0; i < batches.size(); i++) {
                        Bean updated = batches.get(i).copy();
                        updated.name = name.getText().toString();
                        updated.roaster = roaster.getText().toString();
                        updated.beanType = selectedBeanTypes[0];
                        updated.isBlend = isBlend[0];
                        updated.origin = origin.getText().toString();
                        updated.blendDetails = blendDetails.getText().toString();
                        updated.process = process.getText().toString();
                        updated.roastLevel = roast.getText().toString();
                        updated.flavorTags = tags.getText().toString();
                        updated.packageImageUris = joinImages(packageImages.images);
                        updated.beanImageUris = joinImages(beanImages.images);
                        updated.imageUris = updated.packageImageUris;
                        updated.totalGram = num(totals.get(i), updated.totalGram);
                        updated.remainingGram = num(remainings.get(i), updated.remainingGram);
                        updated.price = num(prices.get(i), updated.price);
                        updated.purchaseDate = purchaseDates.get(i).getText().toString();
                        updated.roastDate = roastDates.get(i).getText().toString();
                        updated.openDate = openDates.get(i).getText().toString();
                        updated.bestBeforeDate = bestBeforeDates.get(i).getText().toString();
                        db.updateBean(updated);
                    }
                    Toast.makeText(this, "豆子与批次已更新", Toast.LENGTH_SHORT).show();
                    render();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private LinearLayout batchEditor(int index, Bean batch, List<EditText> totals, List<EditText> remainings,
                                    List<EditText> prices, List<EditText> purchaseDates,
                                    List<EditText> roastDates, List<EditText> openDates,
                                    List<EditText> bestBeforeDates) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(12), 0, dp(2));

        EditText total = numberInput("规格克数", fmt(batch.totalGram));
        EditText remaining = numberInput("剩余克数", fmt(batch.remainingGram));
        EditText price = numberInput("购买价", fmt(batch.price));
        TextView gramPrice = gramPriceField(total, price);
        EditText purchaseDate = input("购买日期 yyyy-MM-dd", batch.purchaseDate);
        EditText roastDate = input("烘焙日期 yyyy-MM-dd", batch.roastDate);
        EditText openDate = input("开封日期 yyyy-MM-dd（留空=未拆封）", batch.openDate);
        EditText bestBeforeDate = bestBeforeInput(roastDate, batch.bestBeforeDate);
        box.addView(formSection("批次 " + index,
                labeled("规格克数", total),
                labeled("剩余库存克数", remaining),
                labeled("购买价", price),
                labeled("克重价格", gramPrice),
                labeled("购买日期", purchaseDate),
                labeled("烘焙日期", roastDate),
                labeled("开封日期", openDate),
                labeled("赏味期限", bestBeforeDate)));

        totals.add(total);
        remainings.add(remaining);
        prices.add(price);
        purchaseDates.add(purchaseDate);
        roastDates.add(roastDate);
        openDates.add(openDate);
        bestBeforeDates.add(bestBeforeDate);
        return box;
    }

    private void showNewBatchDialog(Bean bean) {
        LinearLayout form = form();
        EditText total = numberInput("新批次规格克数", fmt(bean.totalGram));
        EditText remaining = numberInput("新批次入库克数", fmt(bean.totalGram));
        EditText price = numberInput("购买价", fmt(bean.price));
        TextView gramPrice = gramPriceField(total, price);
        EditText purchaseDate = input("购买日期 yyyy-MM-dd", LocalDate.now().toString());
        EditText roastDate = input("烘焙日期 yyyy-MM-dd", LocalDate.now().minusDays(7).toString());
        EditText openDate = input("开封日期 yyyy-MM-dd（留空=未拆封）", "");
        EditText bestBeforeDate = bestBeforeInput(roastDate, "");
        addAll(form,
                formSection("规格与库存",
                        labeled("新批次规格克数", total),
                        labeled("新批次入库克数", remaining),
                        labeled("购买价", price),
                        labeled("克重价格", gramPrice)),
                formSection("日期",
                        labeled("购买日期", purchaseDate),
                        labeled("烘焙日期", roastDate),
                        labeled("开封日期", openDate),
                        labeled("赏味期限", bestBeforeDate)));

        new AlertDialog.Builder(this)
                .setTitle(bean.name + " · 新批次入库")
                .setView(scrollForm(form))
                .setPositiveButton("保存批次", (d, w) -> {
                    Bean batch = bean.copy();
                    batch.id = -1;
                    batch.totalGram = num(total, bean.totalGram);
                    batch.remainingGram = num(remaining, batch.totalGram);
                    batch.price = num(price, bean.price);
                    batch.purchaseDate = purchaseDate.getText().toString();
                    batch.roastDate = roastDate.getText().toString();
                    batch.openDate = openDate.getText().toString();
                    batch.bestBeforeDate = bestBeforeDate.getText().toString();
                    db.addBean(batch);
                    Toast.makeText(this, "新批次已入库", Toast.LENGTH_SHORT).show();
                    render();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmDelete(Bean bean) {
        new AlertDialog.Builder(this)
                .setTitle("删除豆子")
                .setMessage("确定删除「" + bean.name + "」的全部批次吗？相关冲煮记录也会一起删除。")
                .setPositiveButton("删除", (d, w) -> {
                    db.deleteBeans(bean.batchIds());
                    Toast.makeText(this, "豆子已删除", Toast.LENGTH_SHORT).show();
                    render();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showBrewDialog(Bean bean) {
        LinearLayout form = form();
        LinearLayout pick = new LinearLayout(this);
        pick.setOrientation(LinearLayout.HORIZONTAL);
        pick.setGravity(Gravity.CENTER_VERTICAL);
        pick.setPadding(dp(14), dp(12), dp(14), dp(12));
        pick.setBackground(round(PANEL, dp(16), LINE));
        TextView avatar = text(bean.name.substring(0, Math.min(1, bean.name.length())), 17, Color.WHITE, true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(round(accentFor(bean.name), dp(14), Color.TRANSPARENT));
        pick.addView(avatar, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout beanCopy = new LinearLayout(this);
        beanCopy.setOrientation(LinearLayout.VERTICAL);
        beanCopy.setPadding(dp(12), 0, 0, 0);
        beanCopy.addView(text(bean.name, 17, INK, true));
        beanCopy.addView(text("第 1 批 · 剩余 " + fmt(bean.remainingGram) + " / " + fmt(bean.totalGram) + "g", 12, MUTED, false));
        pick.addView(beanCopy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView change = statusBadge("更换", CREAM, GREEN_DARK);
        pick.addView(change);

        EditText method = input("器具", "手冲");
        EditText dose = numberInput("粉量 g", "15");
        EditText water = numberInput("水量 g", "240");
        EditText grind = input("研磨度", "中细");
        EditText temp = numberInput("水温", "92");
        EditText brewTime = input("冲煮时间", LocalTime.now().format(BREW_TIME_FORMAT));
        EditText time = input("萃取时间", "2:30");
        EditText score = numberInput("评分", "4.2");
        EditText note = input("备注", "甜感清楚，尾段干净");
        TextView ratio = gramRatioField(dose, water);
        addAll(form,
                pick,
                formSection("冲煮方式", labeled("方式", method)),
                formSection("记录时间", labeled("时间", brewTime)),
                formSection("粉水", labeled("粉量", dose), labeled("水量", water), labeled("粉水比", ratio)),
                formSection("研磨与水温", labeled("研磨度", grind), labeled("水温", temp), labeled("萃取时间", time)),
                formSection("这一杯怎么样", labeled("评分", score), labeled("风味笔记", note)));

        new AlertDialog.Builder(this)
                .setTitle("记录 " + bean.name)
                .setView(scrollForm(form))
                .setPositiveButton("保存并扣库存", (d, w) -> {
                    db.addBrew(bean.id, LocalDate.now().toString(), brewTime.getText().toString(), method.getText().toString(), num(dose, 15), num(water, 240),
                            grind.getText().toString(), (int) num(temp, 92), time.getText().toString(), num(score, 4), note.getText().toString());
                    Toast.makeText(this, "已记录并扣减库存", Toast.LENGTH_SHORT).show();
                    render();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showTimeline(Bean bean) {
        LinearLayout box = form();
        box.setPadding(dp(12), dp(4), dp(12), dp(4));
        box.addView(text(bean.name, 22, INK, true));
        box.addView(text(bean.roaster + " · " + bean.beanType + " · " + bean.origin + " · " + bean.process, 14, MUTED, false));
        if (!isBlank(bean.blendDetails)) box.addView(text("拼配构成：" + bean.blendDetails, 13, GREEN_DARK, false));
        box.addView(tagRow(bean.flavorTags));

        TextView batchTitle = text("批次节点", 15, GREEN_DARK, true);
        batchTitle.setPadding(0, dp(8), 0, dp(6));
        box.addView(batchTitle);
        box.addView(batchTimeline(bean));

        TextView brewTitle = text("冲煮记录", 15, GREEN_DARK, true);
        brewTitle.setPadding(0, dp(12), 0, dp(6));
        box.addView(brewTitle);
        List<Brew> brews = db.brewsForBeans(20, bean.batchIds());
        box.addView(brewTimelineList(brews, false));

        new AlertDialog.Builder(this)
                .setView(scrollForm(box))
                .setPositiveButton("好", null)
                .show();
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(round(CARD, dp(14), Color.argb(118, 255, 255, 255)));
        return card;
    }

    private LinearLayout formSection(String title, View... children) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(14), dp(12), dp(14), dp(12));
        section.setBackground(round(PANEL, dp(14), LINE));

        TextView heading = text(title, 13, GREEN_DARK, true);
        heading.setPadding(0, 0, 0, dp(6));
        section.addView(heading);
        for (View child : children) section.addView(child, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(7), 0, dp(7));
        section.setLayoutParams(lp);
        return section;
    }

    private TextView textPill(String copy) {
        TextView t = text(copy, 15, INK, false);
        t.setPadding(dp(14), dp(12), dp(14), dp(12));
        t.setBackground(round(PANEL, dp(16), LINE));
        return t;
    }

    private LinearLayout tagRow(String tags) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(12), 0, dp(12));
        for (String tag : tags.split(",")) {
            TextView pill = text(tag.trim(), 12, Color.WHITE, true);
            pill.setGravity(Gravity.CENTER);
            pill.setPadding(dp(11), dp(6), dp(11), dp(6));
            pill.setBackground(round(accentFor(tag), dp(16), Color.argb(80, 255, 255, 255)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(0, 0, dp(7), 0);
            row.addView(pill, lp);
        }
        return row;
    }

    private void addMetric(LinearLayout parent, String value, String label) {
        LinearLayout m = new LinearLayout(this);
        m.setOrientation(LinearLayout.VERTICAL);
        m.addView(text(value, 17, INK, true));
        m.addView(text(label, 12, MUTED, false));
        parent.addView(m, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void addMetricBlock(LinearLayout parent, String firstValue, String firstLabel, String secondValue, String secondLabel, int fill, int ink) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(10), dp(9), dp(10), dp(9));
        block.setBackground(round(fill, dp(12), Color.TRANSPARENT));

        LinearLayout first = metricLine(firstValue, firstLabel, ink);
        LinearLayout second = metricLine(secondValue, secondLabel, ink);
        LinearLayout.LayoutParams secondLp = new LinearLayout.LayoutParams(-1, -2);
        secondLp.setMargins(0, dp(6), 0, 0);
        block.addView(first);
        block.addView(second, secondLp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(parent.getChildCount() == 0 ? 0 : dp(8), 0, 0, 0);
        parent.addView(block, lp);
    }

    private LinearLayout metricLine(String value, String label, int ink) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView labelView = text(label, 11, MUTED, true);
        TextView valueView = text(value, 15, ink, true);
        valueView.setGravity(Gravity.RIGHT);
        line.addView(labelView, new LinearLayout.LayoutParams(0, -2, 1));
        line.addView(valueView);
        return line;
    }

    private View stockRing(Bean bean) {
        double remaining = bean.totalGram <= 0 ? 0 : Math.max(0, Math.min(1, bean.remainingGram / bean.totalGram));
        return new RingView(this, remaining, GREEN, CREAM, INK, String.format(Locale.CHINA, "%.0f%%", remaining * 100));
    }

    private LinearLayout batchList(Bean bean) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(10), 0, 0);
        List<Bean> batches = bean.batchesOrSelf();
        for (int i = 0; i < batches.size(); i++) {
            Bean batch = batches.get(i);
            TextView line = text(String.format(Locale.CHINA, "批次%d · 剩余%s/%sg · %s · 烘焙%s · 赏味至%s · %s",
                    i + 1, fmt(batch.remainingGram), fmt(batch.totalGram), gramPriceText(batch.totalGram, batch.price),
                    batch.roastDate, cleanBestBefore(batch), openState(batch)), 12, MUTED, false);
            line.setPadding(dp(10), dp(6), dp(10), dp(6));
            line.setBackground(round(PANEL, dp(10), LINE));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, i == 0 ? 0 : dp(5), 0, 0);
            box.addView(line, lp);
        }
        return box;
    }

    private void sectionTitle(String title, String sub) {
        TextView t = text(title, 23, INK, true);
        t.setPadding(0, dp(12), 0, 0);
        content.addView(t);
        TextView s = text(sub, 13, MUTED, false);
        s.setPadding(0, dp(2), 0, dp(10));
        content.addView(s);
    }

    private void pageTitle(String title, String sub) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(14));
        box.setBackground(round(Color.argb(68, 255, 255, 255), dp(16), LINE));
        TextView t = text(title, 28, INK, true);
        box.addView(t);
        TextView s = text(sub, 13, MUTED, false);
        s.setPadding(0, dp(4), 0, 0);
        box.addView(s);
        content.addView(box, fullMargin());
    }

    private String shortBeanType(String raw) {
        String value = normalizeBeanTypes(raw);
        return value.replaceAll("\\s*/\\s*[A-Za-z][A-Za-z .'-]*", "").trim();
    }

    private String drinkHint(Bean bean) {
        String window = drinkingWindow(bean);
        if ("过峰值".equals(window) || "尾段".equals(window)) return "已过赏味期，建议先喝完它，再开新的。";
        if ("养豆中".equals(window)) return "还在养豆中，想尝鲜可以先少量试冲。";
        return "状态正好，建议今天安排一杯。";
    }

    private Button primaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setPadding(dp(16), 0, dp(16), 0);
        b.setMinHeight(dp(44));
        b.setElevation(0);
        b.setStateListAnimator(null);
        b.setBackground(round(Color.argb(188, 34, 157, 139), dp(14), Color.argb(96, 255, 255, 255)));
        return b;
    }

    private Button smallButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(11);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setPadding(dp(6), 0, dp(6), 0);
        b.setMinHeight(dp(34));
        b.setElevation(0);
        b.setStateListAnimator(null);
        b.setBackground(round(Color.argb(178, 34, 157, 139), dp(12), Color.argb(86, 255, 255, 255)));
        return b;
    }

    private Button subtleButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(11);
        b.setTextColor(GREEN_DARK);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setPadding(dp(6), 0, dp(6), 0);
        b.setMinHeight(dp(34));
        b.setBackground(round(CREAM, dp(12), Color.rgb(220, 207, 185)));
        return b;
    }

    private TextView choiceField(String label) {
        TextView v = text(label + "  ▾", 14, INK, false);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(dp(12), 0, dp(12), 0);
        v.setMinHeight(dp(46));
        v.setBackground(round(BG, dp(8), LINE));
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    private TextView iconButton(String icon, int ink, int fill, int stroke) {
        TextView b = text(icon, 20, ink, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(round(fill, dp(13), stroke));
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    private TextView circleButton(String icon, int ink, int fill, int stroke) {
        TextView b = text(icon, 18, ink, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(round(fill, dp(24), stroke));
        b.setClickable(true);
        b.setFocusable(true);
        b.setElevation(0);
        return b;
    }

    private TextView flatCircleButton(String icon, int ink, int fill, int stroke) {
        TextView b = text(icon, 16, ink, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(round(fill, dp(20), stroke));
        b.setClickable(true);
        b.setFocusable(true);
        b.setElevation(0);
        return b;
    }

    private LinearLayout.LayoutParams actionTextParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(34), 1);
        lp.setMargins(0, 0, dp(7), 0);
        return lp;
    }

    private LinearLayout.LayoutParams iconParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(36), dp(34));
        lp.setMargins(dp(2), 0, 0, 0);
        return lp;
    }

    private GradientDrawable round(int fill, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius);
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }

    private TextView text(String copy, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(copy);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(true);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private EditText input(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(true);
        e.setTextSize(14);
        e.setTextColor(INK);
        e.setHintTextColor(MUTED);
        e.setPadding(dp(12), 0, dp(12), 0);
        e.setMinHeight(dp(46));
        e.setBackground(round(BG, dp(8), LINE));
        return e;
    }

    private EditText numberInput(String hint, String value) {
        EditText e = input(hint, value);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return e;
    }

    private void showBeanTypeDialog(TextView trigger, String[] selectedBeanTypes) {
        int selected = 0;
        String normalized = normalizeBeanTypes(selectedBeanTypes[0]);
        for (int i = 0; i < BEAN_TYPES.length; i++) if (BEAN_TYPES[i].equals(normalized)) selected = i;
        new AlertDialog.Builder(this)
                .setTitle("选择豆种")
                .setSingleChoiceItems(BEAN_TYPES, selected, (dialog, which) -> {
                    selectedBeanTypes[0] = BEAN_TYPES[which];
                    trigger.setText(selectedBeanTypes[0] + "  ▾");
                    dialog.dismiss();
                })
                .show();
    }

    private void showProductTypeDialog(TextView trigger, boolean[] isBlend) {
        String[] options = {"单品", "拼配"};
        new AlertDialog.Builder(this)
                .setTitle("选择产品类型")
                .setSingleChoiceItems(options, isBlend[0] ? 1 : 0, (dialog, which) -> {
                    isBlend[0] = which == 1;
                    trigger.setText(options[which] + "  ▾");
                    dialog.dismiss();
                }).show();
    }

    private EditText bestBeforeInput(EditText roastDate, String value) {
        String initial = isBlank(value) ? bestBeforeFrom(roastDate.getText().toString()) : value;
        EditText e = input("赏味期限 yyyy-MM-dd", initial);
        final String[] lastAuto = {isBlank(value) ? initial : ""};
        roastDate.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String current = e.getText().toString().trim();
                if (!isBlank(current) && !current.equals(lastAuto[0])) return;
                String next = bestBeforeFrom(s.toString());
                lastAuto[0] = next;
                e.setText(next);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        return e;
    }

    private TextView gramPriceField(EditText total, EditText price) {
        TextView out = text("", 15, GREEN_DARK, true);
        out.setGravity(Gravity.CENTER_VERTICAL);
        out.setPadding(dp(12), 0, dp(12), 0);
        out.setMinHeight(dp(46));
        out.setBackground(round(BG, dp(8), LINE));
        Runnable refresh = () -> out.setText(gramPriceText(num(total, 0), num(price, 0)));
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                refresh.run();
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        total.addTextChangedListener(watcher);
        price.addTextChangedListener(watcher);
        refresh.run();
        return out;
    }

    private TextView gramRatioField(EditText dose, EditText water) {
        TextView out = text("", 15, INK, true);
        out.setGravity(Gravity.CENTER_VERTICAL);
        out.setPadding(dp(12), 0, dp(12), 0);
        out.setMinHeight(dp(46));
        out.setBackground(round(SAND, dp(8), LINE));
        Runnable refresh = () -> {
            double d = num(dose, 0);
            double w = num(water, 0);
            out.setText(d <= 0 || w <= 0 ? "-" : String.format(Locale.CHINA, "1 : %.1f", w / d));
        };
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                refresh.run();
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        dose.addTextChangedListener(watcher);
        water.addTextChangedListener(watcher);
        refresh.run();
        return out;
    }

    private String gramPriceText(double totalGram, double price) {
        if (totalGram <= 0 || price <= 0) return "- / g";
        return String.format(Locale.CHINA, "¥ %.2f / g", price / totalGram);
    }

    private String bestBeforeFrom(String roastDate) {
        return date(roastDate).plusDays(45).toString();
    }

    private boolean[] checkedTypes(String value) {
        boolean[] checked = new boolean[BEAN_TYPES.length];
        String normalized = normalizeBeanTypes(value);
        for (int i = 0; i < BEAN_TYPES.length; i++) {
            checked[i] = normalized.contains(BEAN_TYPES[i]);
        }
        return checked;
    }

    private String joinTypes(boolean[] checked) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < BEAN_TYPES.length; i++) {
            if (!checked[i]) continue;
            if (out.length() > 0) out.append("、");
            out.append(BEAN_TYPES[i]);
        }
        return out.length() == 0 ? DEFAULT_BEAN_TYPE : out.toString();
    }

    private String normalizeBeanTypes(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || value.contains("埃塞俄比亚阿拉比卡土基因库")) return DEFAULT_BEAN_TYPE;
        for (String type : BEAN_TYPES) if (value.contains(type)) return type;
        return DEFAULT_BEAN_TYPE;
    }

    private LinearLayout labeled(String label, View child) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(6), 0, dp(4));
        box.addView(text(label, 12, MUTED, true));
        box.addView(child, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }

    private LinearLayout form() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(6), dp(4), dp(6), dp(4));
        return form;
    }

    private ScrollView scrollForm(LinearLayout form) {
        ScrollView scroll = new ScrollView(this) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int maxHeight = Math.max(dp(260), (int) (getResources().getDisplayMetrics().heightPixels * 0.58f));
                int cappedHeightSpec = View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST);
                super.onMeasure(widthMeasureSpec, cappedHeightSpec);
            }
        };
        scroll.setFillViewport(false);
        scroll.setPadding(0, 0, 0, dp(8));
        scroll.addView(form);
        return scroll;
    }

    private void addAll(LinearLayout form, View... views) {
        for (View v : views) {
            if (v.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                form.addView(v);
            } else {
                form.addView(v, new LinearLayout.LayoutParams(-1, -2));
            }
        }
    }

    private LinearLayout.LayoutParams fullMargin() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(6), 0, dp(10));
        return lp;
    }

    private int accentFor(String key) {
        int[] colors = {
                Color.rgb(47, 122, 105),
                Color.rgb(201, 91, 72),
                Color.rgb(92, 109, 171),
                Color.rgb(186, 128, 47),
                Color.rgb(129, 91, 142)
        };
        return colors[Math.abs(key.hashCode()) % colors.length];
    }

    private String drinkingWindow(Bean bean) {
        LocalDate now = LocalDate.now();
        long roastDay = ChronoUnit.DAYS.between(date(bean.roastDate), now);
        long daysLeft = ChronoUnit.DAYS.between(now, date(cleanBestBefore(bean)));
        if (roastDay < 7) return "养豆中";
        if (daysLeft >= 0) return "黄金期";
        if (daysLeft >= -25) return "尾段";
        return "过峰值";
    }

    private String stockStatus(Bean bean) {
        if (isConsumed(bean)) return "已消耗完";
        String low = bean.remainingGram < 45 ? "低库存 · " : "";
        return low + drinkingWindow(bean);
    }

    private String openState(Bean bean) {
        return isBlank(bean.openDate) ? "未拆封" : "开封 " + bean.openDate;
    }

    private String cleanBestBefore(Bean bean) {
        return isBlank(bean.bestBeforeDate) ? bestBeforeFrom(bean.roastDate) : bean.bestBeforeDate;
    }

    private int statusColor(Bean bean) {
        if (isConsumed(bean)) return MUTED;
        if (bean.remainingGram < 45) return ROSE;
        String window = drinkingWindow(bean);
        if ("黄金期".equals(window)) return GREEN;
        if ("尾段".equals(window)) return AMBER;
        return MUTED;
    }

    private String stockGroup(Bean bean) {
        if (isConsumed(bean)) return "consumed";
        String window = drinkingWindow(bean);
        if (bean.remainingGram < 45 || "尾段".equals(window)) return "priority";
        if ("黄金期".equals(window)) return "ready";
        if ("养豆中".equals(window)) return "resting";
        return "past";
    }

    private String bestRecipe(Bean bean) {
        Brew b = db.bestBrewForBeans(bean.batchIds());
        if (b == null) return "还没有记录，建议从 15g 粉 / 240g 水开始";
        return String.format(Locale.CHINA, "%s %.1fg粉 %.0fg水 %s %.1f分", b.method, b.dose, b.water, b.grind, b.score);
    }

    private int bestRecipeCups(Bean bean) {
        Brew b = db.bestBrewForBeans(bean.batchIds());
        double dose = b == null || b.dose <= 0 ? 15 : b.dose;
        return Math.max(0, (int) Math.floor(bean.remainingGram / dose));
    }

    private String batchCount(Bean bean) {
        int count = bean.batchesOrSelf().size();
        return count + " 批";
    }

    private Bean drinkBatch(Bean bean) {
        Bean pick = null;
        for (Bean batch : bean.batchesOrSelf()) {
            if (batch.remainingGram <= 0) continue;
            if (pick == null || date(batch.roastDate).isBefore(date(pick.roastDate))) pick = batch;
        }
        return pick == null ? bean.batchesOrSelf().get(0) : pick;
    }

    private LocalDate date(String raw) {
        try {
            String value = raw == null ? "" : raw.trim();
            if (value.length() >= 10) value = value.substring(0, 10);
            return LocalDate.parse(value);
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    private static String cleanBrewTime(String raw) {
        try {
            String value = raw == null ? "" : raw.trim();
            if (value.matches("\\d{1,2}:\\d{2}")) {
                String[] parts = value.split(":");
                int hour = Integer.parseInt(parts[0]);
                int minute = Integer.parseInt(parts[1]);
                return LocalTime.of(hour, minute).format(BREW_TIME_FORMAT);
            }
        } catch (Exception ignored) {
        }
        return LocalTime.now().format(BREW_TIME_FORMAT);
    }

    private static String cleanBrewDate(String raw) {
        try {
            return LocalDate.parse(raw == null ? "" : raw.trim()).toString();
        } catch (Exception ignored) {
            return LocalDate.now().toString();
        }
    }

    private double num(EditText e, double fallback) {
        try {
            return Double.parseDouble(e.getText().toString().trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String fmt(double value) {
        return String.format(Locale.CHINA, "%.0f", value);
    }

    private static boolean isBlank(String raw) {
        return raw == null || raw.trim().isEmpty();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    class RingView extends View {
        private final double progress;
        private final int arcColor;
        private final int trackColor;
        private final int textColor;
        private final String label;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        RingView(Context context, double progress, int arcColor, int trackColor, int textColor, String label) {
            super(context);
            this.progress = Math.max(0, Math.min(1, progress));
            this.arcColor = arcColor;
            this.trackColor = trackColor;
            this.textColor = textColor;
            this.label = label;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int size = Math.min(getWidth(), getHeight());
            float stroke = dp(5);
            float inset = stroke / 2f + dp(2);
            RectF oval = new RectF(inset, inset, size - inset, size - inset);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(trackColor);
            canvas.drawArc(oval, -90, 360, false, paint);
            paint.setColor(arcColor);
            canvas.drawArc(oval, -90, (float) (progress * 360), false, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(10));
            paint.setColor(textColor);
            Paint.FontMetrics fm = paint.getFontMetrics();
            canvas.drawText(label, size / 2f, size / 2f - (fm.ascent + fm.descent) / 2f, paint);
        }
    }

    class WorldMapView extends View {
        private final List<ChartItem> items;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        WorldMapView(Context context, List<ChartItem> items) {
            super(context);
            this.items = items;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            paint.setStyle(Paint.Style.FILL);
            double max = 1;
            for (ChartItem item : items) max = Math.max(max, item.value);
            for (ChartItem item : items) {
                float[] point = mapPoint(item.label);
                float x = w * point[0];
                float y = h * point[1];
                float r = dp(6) + (float) (dp(10) * item.value / max);
                paint.setColor(Color.argb(90, 34, 157, 139));
                canvas.drawCircle(x, y, r + dp(4), paint);
                paint.setColor(GREEN);
                canvas.drawCircle(x, y, r, paint);
                paint.setColor(Color.WHITE);
                canvas.drawCircle(x, y, dp(3), paint);
            }
        }

        private float[] mapPoint(String origin) {
            String v = origin == null ? "" : origin.toLowerCase(Locale.ROOT);
            if (v.contains("巴西") || v.contains("brazil")) return new float[]{.33f, .64f};
            if (v.contains("哥伦比亚") || v.contains("colombia")) return new float[]{.28f, .53f};
            if (v.contains("巴拿马") || v.contains("panama")) return new float[]{.25f, .50f};
            if (v.contains("危地马拉") || v.contains("guatemala")) return new float[]{.23f, .45f};
            if (v.contains("哥斯达黎加") || v.contains("costa rica")) return new float[]{.25f, .48f};
            if (v.contains("埃塞") || v.contains("ethiopia")) return new float[]{.56f, .56f};
            if (v.contains("肯尼亚") || v.contains("kenya")) return new float[]{.59f, .60f};
            if (v.contains("卢旺达") || v.contains("rwanda")) return new float[]{.55f, .62f};
            if (v.contains("印尼") || v.contains("印度尼西亚") || v.contains("indonesia")) return new float[]{.79f, .63f};
            if (v.contains("也门") || v.contains("yemen")) return new float[]{.62f, .50f};
            if (v.contains("中国") || v.contains("china")) return new float[]{.74f, .43f};
            return new float[]{.52f, .50f};
        }
    }

    class PieChartView extends View {
        private final List<ChartItem> items;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        PieChartView(Context context, List<ChartItem> items) {
            super(context);
            this.items = items;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            double total = 0;
            for (ChartItem item : items) total += item.value;
            if (total <= 0) return;
            int size = Math.min(getWidth(), getHeight());
            float inset = dp(8);
            RectF oval = new RectF(inset, inset, size - inset, size - inset);
            float start = -90;
            int[] colors = pieColors();
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < items.size(); i++) {
                float sweep = (float) (items.get(i).value / total * 360);
                paint.setColor(colors[i % colors.length]);
                canvas.drawArc(oval, start, sweep, true, paint);
                start += sweep;
            }
            paint.setColor(Color.argb(190, 49, 39, 33));
            canvas.drawCircle(size / 2f, size / 2f, size * .22f, paint);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(12));
            paint.setColor(INK);
            Paint.FontMetrics fm = paint.getFontMetrics();
            canvas.drawText(formatChartValue(total) + "项", size / 2f, size / 2f - (fm.ascent + fm.descent) / 2f, paint);
        }
    }

    static class Bean {
        long id;
        String name;
        String roaster;
        String beanType;
        String origin;
        String process;
        String roastLevel;
        String flavorTags;
        String blendDetails;
        boolean isBlend;
        String imageUris;
        String packageImageUris;
        String beanImageUris;
        double totalGram;
        double remainingGram;
        double price;
        String purchaseDate;
        String roastDate;
        String openDate;
        String bestBeforeDate;
        List<Bean> batches;

        Bean copy() {
            Bean b = new Bean();
            b.id = id;
            b.name = name;
            b.roaster = roaster;
            b.beanType = beanType;
            b.origin = origin;
            b.process = process;
            b.roastLevel = roastLevel;
            b.flavorTags = flavorTags;
            b.blendDetails = blendDetails;
            b.isBlend = isBlend;
            b.imageUris = imageUris;
            b.packageImageUris = packageImageUris;
            b.beanImageUris = beanImageUris;
            b.totalGram = totalGram;
            b.remainingGram = remainingGram;
            b.price = price;
            b.purchaseDate = purchaseDate;
            b.roastDate = roastDate;
            b.openDate = openDate;
            b.bestBeforeDate = bestBeforeDate;
            if (batches != null) b.batches = new ArrayList<>(batches);
            return b;
        }

        String groupKey() {
            return cleanKey(name) + "|" + cleanKey(roaster) + "|" + isBlend + "|" + cleanKey(beanType) + "|" + cleanKey(origin) + "|" + cleanKey(process) + "|" + cleanKey(roastLevel) + "|" + cleanKey(flavorTags) + "|" + cleanKey(blendDetails);
        }

        List<Bean> batchesOrSelf() {
            if (batches != null && !batches.isEmpty()) return batches;
            List<Bean> out = new ArrayList<>();
            out.add(this);
            return out;
        }

        long[] batchIds() {
            List<Bean> list = batchesOrSelf();
            long[] ids = new long[list.size()];
            for (int i = 0; i < list.size(); i++) ids[i] = list.get(i).id;
            return ids;
        }

        void addBatch(Bean batch) {
            if (batches == null) batches = new ArrayList<>();
            if (batches.isEmpty()) {
                Bean self = copy();
                self.batches = null;
                batches.add(self);
            }
            batches.add(batch);
            totalGram += batch.totalGram;
            remainingGram += batch.remainingGram;
            price += batch.price;
            if (isBlank(imageUris) && !isBlank(batch.imageUris)) imageUris = batch.imageUris;
            if (LocalDate.parse(batch.roastDate).isBefore(LocalDate.parse(roastDate))) {
                roastDate = batch.roastDate;
                purchaseDate = batch.purchaseDate;
                openDate = batch.openDate;
                bestBeforeDate = batch.bestBeforeDate;
            }
        }

        private static String cleanKey(String raw) {
            return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        }
    }

    static class Brew {
        long id;
        long beanId;
        String beanName;
        String date;
        String brewTime;
        String method;
        double dose;
        double water;
        String grind;
        int temp;
        String time;
        double score;
        String note;
        String imageUris;

        String dateLabel() {
            return isBlank(brewTime) ? date : date + " " + brewTime;
        }
    }

    static class Stats {
        double totalRemaining;
        double monthUsed;
        int brewCount;
        double avgScore;
    }

    static class RecipeStock {
        String method;
        int cups;
    }

    static class ChartItem {
        String label;
        double value;

        ChartItem(String label, double value) {
            this.label = label;
            this.value = value;
        }
    }

    static class FutureBeanEvent {
        LocalDate date;
        String title, detail, note, marker;
        int fill, ink;
        FutureBeanEvent(LocalDate date, String title, String detail, String note, String marker, int fill, int ink) {
            this.date = date; this.title = title; this.detail = detail; this.note = note;
            this.marker = marker; this.fill = fill; this.ink = ink;
        }
    }

    static class BrewInputs {
        String method;
        String grind;
        int score;
        List<String> noteTags;
    }

    static class CoffeeDb extends SQLiteOpenHelper {
        CoffeeDb(Context context) {
            super(context, "coffee_cellar.db", null, 10);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE beans(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,roaster TEXT,bean_type TEXT,is_blend INTEGER DEFAULT 0,origin TEXT,process TEXT,roast_level TEXT,flavor_tags TEXT,blend_details TEXT,image_uris TEXT,package_image_uris TEXT,bean_image_uris TEXT,total_gram REAL,remaining_gram REAL,price REAL,purchase_date TEXT,roast_date TEXT,open_date TEXT,best_before_date TEXT)");
            db.execSQL("CREATE TABLE brews(id INTEGER PRIMARY KEY AUTOINCREMENT,bean_id INTEGER,date TEXT,brew_time TEXT,method TEXT,dose REAL,water REAL,grind TEXT,temp INTEGER,time TEXT,score REAL,note TEXT,image_uris TEXT)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE beans ADD COLUMN bean_type TEXT DEFAULT '铁皮卡 / Typica'");
            }
            if (oldVersion < 3) {
                db.execSQL("UPDATE beans SET bean_type = '铁皮卡 / Typica' WHERE bean_type IS NULL OR bean_type = '' OR bean_type = '埃塞俄比亚阿拉比卡土基因库'");
            }
            if (oldVersion < 4) {
                db.execSQL("ALTER TABLE beans ADD COLUMN best_before_date TEXT");
                db.execSQL("UPDATE beans SET best_before_date = date(roast_date, '+45 days') WHERE best_before_date IS NULL OR best_before_date = ''");
            }
            if (oldVersion < 5) {
                db.execSQL("ALTER TABLE beans ADD COLUMN image_uris TEXT");
            }
            if (oldVersion < 6) {
                db.execSQL("ALTER TABLE brews ADD COLUMN brew_time TEXT");
                db.execSQL("UPDATE brews SET brew_time = '00:00' WHERE brew_time IS NULL OR brew_time = ''");
            }
            if (oldVersion < 7) {
                db.execSQL("ALTER TABLE beans ADD COLUMN package_image_uris TEXT");
                db.execSQL("ALTER TABLE beans ADD COLUMN bean_image_uris TEXT");
                db.execSQL("UPDATE beans SET package_image_uris = image_uris WHERE package_image_uris IS NULL OR package_image_uris = ''");
            }
            if (oldVersion < 8) db.execSQL("ALTER TABLE brews ADD COLUMN image_uris TEXT");
            if (oldVersion < 9) db.execSQL("ALTER TABLE beans ADD COLUMN blend_details TEXT");
            if (oldVersion < 10) db.execSQL("ALTER TABLE beans ADD COLUMN is_blend INTEGER DEFAULT 0");
        }

        void seedIfEmpty() {
            Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM beans", null);
            c.moveToFirst();
            boolean empty = c.getInt(0) == 0;
            c.close();
            if (!empty) return;
            LocalDate now = LocalDate.now();
            long a = addBean("花蝴蝶", "Fisher Coffee", "埃塞俄比亚", "日晒", "浅烘", "莓果,花香,柑橘", 200, 88, now.minusDays(12).toString(), now.minusDays(10).toString(), now.minusDays(3).toString());
            long b = addBean("曼特宁 G1", "M Stand", "印尼", "湿刨", "中深烘", "黑巧,香料,奶油", 250, 108, now.minusDays(28).toString(), now.minusDays(24).toString(), now.minusDays(14).toString());
            long cId = addBean("瑰夏拼配", "乔治队长", "巴拿马", "水洗", "浅中烘", "白花,蜂蜜,绿茶", 100, 128, now.minusDays(6).toString(), now.minusDays(4).toString(), now.toString());
            addBrew(a, now.minusDays(1).toString(), "09:18", "手冲", 15, 240, "中细", 92, "2:32", 4.5, "莓果酸质漂亮，尾段很干净");
            addBrew(b, now.minusDays(2).toString(), "14:42", "摩卡壶", 18, 120, "细", 90, "3:10", 4.0, "厚度好，适合加奶");
            addBrew(cId, now.toString(), "20:06", "手冲", 15, 225, "中细", 91, "2:25", 4.3, "还在养豆，香气已经出来了");
        }

        long addBean(String name, String roaster, String origin, String process, String roastLevel, String tags, double total, double price, String purchaseDate, String roastDate, String openDate) {
            Bean bean = new Bean();
            bean.name = name;
            bean.roaster = roaster;
            bean.beanType = DEFAULT_BEAN_TYPE;
            bean.origin = origin;
            bean.process = process;
            bean.roastLevel = roastLevel;
            bean.flavorTags = tags;
            bean.totalGram = total;
            bean.remainingGram = total;
            bean.price = price;
            bean.purchaseDate = purchaseDate;
            bean.roastDate = roastDate;
            bean.openDate = openDate;
            bean.bestBeforeDate = parse(roastDate).plusDays(45).toString();
            return addBean(bean);
        }

        long addBean(Bean bean) {
            ContentValues v = new ContentValues();
            putBeanValues(v, bean);
            return getWritableDatabase().insert("beans", null, v);
        }

        void updateBean(Bean bean) {
            ContentValues v = new ContentValues();
            putBeanValues(v, bean);
            getWritableDatabase().update("beans", v, "id = ?", new String[]{String.valueOf(bean.id)});
        }

        void updateBeanBase(Bean bean) {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues v = new ContentValues();
            v.put("name", clean(bean.name, "未命名豆子"));
            v.put("roaster", clean(bean.roaster, "未知烘焙商"));
            v.put("bean_type", clean(bean.beanType, DEFAULT_BEAN_TYPE));
            v.put("is_blend", bean.isBlend ? 1 : 0);
            v.put("origin", clean(bean.origin, "未知产地"));
            v.put("process", clean(bean.process, "未知处理"));
            v.put("roast_level", clean(bean.roastLevel, "未知烘焙"));
            v.put("flavor_tags", clean(bean.flavorTags, "风味待补充"));
            v.put("blend_details", cleanOptional(bean.blendDetails));
            v.put("image_uris", cleanOptional(bean.imageUris));
            v.put("package_image_uris", cleanOptional(bean.packageImageUris));
            v.put("bean_image_uris", cleanOptional(bean.beanImageUris));
            for (long id : bean.batchIds()) {
                db.update("beans", v, "id = ?", new String[]{String.valueOf(id)});
            }
        }

        void deleteBean(long beanId) {
            SQLiteDatabase db = getWritableDatabase();
            db.delete("brews", "bean_id = ?", new String[]{String.valueOf(beanId)});
            db.delete("beans", "id = ?", new String[]{String.valueOf(beanId)});
        }

        void deleteBeans(long[] beanIds) {
            for (long id : beanIds) deleteBean(id);
        }

        private void putBeanValues(ContentValues v, Bean bean) {
            v.put("name", clean(bean.name, "未命名豆子"));
            v.put("roaster", clean(bean.roaster, "未知烘焙商"));
            v.put("bean_type", clean(bean.beanType, DEFAULT_BEAN_TYPE));
            v.put("is_blend", bean.isBlend ? 1 : 0);
            v.put("origin", clean(bean.origin, "未知产地"));
            v.put("process", clean(bean.process, "未知处理"));
            v.put("roast_level", clean(bean.roastLevel, "未知烘焙"));
            v.put("flavor_tags", clean(bean.flavorTags, "风味待补充"));
            v.put("blend_details", cleanOptional(bean.blendDetails));
            v.put("image_uris", cleanOptional(bean.imageUris));
            v.put("package_image_uris", cleanOptional(bean.packageImageUris));
            v.put("bean_image_uris", cleanOptional(bean.beanImageUris));
            v.put("total_gram", Math.max(bean.totalGram, 0));
            v.put("remaining_gram", Math.max(bean.remainingGram, 0));
            v.put("price", Math.max(bean.price, 0));
            v.put("purchase_date", clean(bean.purchaseDate, LocalDate.now().toString()));
            v.put("roast_date", clean(bean.roastDate, LocalDate.now().toString()));
            v.put("open_date", cleanOptional(bean.openDate));
            v.put("best_before_date", clean(bean.bestBeforeDate, parse(bean.roastDate).plusDays(45).toString()));
        }

        void addBrew(long beanId, String date, String brewTime, String method, double dose, double water, String grind, int temp, String time, double score, String note) {
            addBrew(beanId, date, brewTime, method, dose, water, grind, temp, time, score, note, "");
        }

        void addBrew(long beanId, String date, String brewTime, String method, double dose, double water, String grind, int temp, String time, double score, String note, String imageUris) {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues v = new ContentValues();
            v.put("bean_id", beanId);
            v.put("date", cleanBrewDate(date));
            v.put("brew_time", cleanBrewTime(brewTime));
            v.put("method", clean(method, "手冲"));
            v.put("dose", dose);
            v.put("water", water);
            v.put("grind", clean(grind, "未记录"));
            v.put("temp", temp);
            v.put("time", clean(time, "未记录"));
            v.put("score", score);
            v.put("note", clean(note, ""));
            v.put("image_uris", cleanOptional(imageUris));
            db.insert("brews", null, v);
            db.execSQL("UPDATE beans SET remaining_gram = MAX(0, remaining_gram - ?) WHERE id = ?", new Object[]{dose, beanId});
        }

        void updateBrewDateTime(long brewId, String date, String brewTime) {
            ContentValues v = new ContentValues();
            v.put("date", cleanBrewDate(date));
            v.put("brew_time", cleanBrewTime(brewTime));
            getWritableDatabase().update("brews", v, "id = ?", new String[]{String.valueOf(brewId)});
        }

        void moveBrewToBean(long brewId, long oldBeanId, long newBeanId, double dose) {
            if (oldBeanId == newBeanId) return;
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                ContentValues v = new ContentValues();
                v.put("bean_id", newBeanId);
                db.update("brews", v, "id = ?", new String[]{String.valueOf(brewId)});
                db.execSQL("UPDATE beans SET remaining_gram = MIN(total_gram, remaining_gram + ?) WHERE id = ?", new Object[]{dose, oldBeanId});
                db.execSQL("UPDATE beans SET remaining_gram = MAX(0, remaining_gram - ?) WHERE id = ?", new Object[]{dose, newBeanId});
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }

        List<Bean> beans() {
            return queryBeans("SELECT * FROM beans ORDER BY name");
        }

        List<Bean> beanGroups() {
            List<Bean> raw = queryBeans("SELECT * FROM beans ORDER BY name, roast_date ASC, id ASC");
            List<Bean> groups = new ArrayList<>();
            for (Bean bean : raw) {
                Bean group = null;
                for (Bean candidate : groups) {
                    if (candidate.groupKey().equals(bean.groupKey())) {
                        group = candidate;
                        break;
                    }
                }
                if (group == null) {
                    Bean first = bean.copy();
                    first.batches = new ArrayList<>();
                    first.batches.add(bean);
                    groups.add(first);
                } else {
                    group.addBatch(bean);
                }
            }
            return groups;
        }

        List<Bean> beansByPriority() {
            return queryBeans("SELECT * FROM beans ORDER BY remaining_gram ASC, roast_date ASC");
        }

        List<Bean> beanGroupsByPriority() {
            List<Bean> groups = beanGroups();
            Collections.sort(groups, (a, b) -> {
                int remaining = Double.compare(a.remainingGram, b.remainingGram);
                if (remaining != 0) return remaining;
                return parse(a.roastDate).compareTo(parse(b.roastDate));
            });
            return groups;
        }

        Bean recommendedBean() {
            List<Bean> list = queryBeans("SELECT * FROM beans WHERE remaining_gram > 0 ORDER BY roast_date ASC LIMIT 1");
            return list.isEmpty() ? null : list.get(0);
        }

        Bean beanById(long id) {
            List<Bean> list = queryBeans("SELECT * FROM beans WHERE id = " + id + " LIMIT 1");
            return list.isEmpty() ? null : list.get(0);
        }

        List<Bean> availableBatches() {
            return queryBeans("SELECT * FROM beans WHERE remaining_gram > 0 ORDER BY roast_date ASC, name ASC, id ASC");
        }

        List<Bean> sameGroupBatches(Bean bean) {
            List<Bean> out = new ArrayList<>();
            for (Bean candidate : queryBeans("SELECT * FROM beans ORDER BY roast_date ASC, id ASC")) {
                if (candidate.groupKey().equals(bean.groupKey())) out.add(candidate);
            }
            return out;
        }

        List<Brew> brews(int limit, long beanId) {
            String sql = "SELECT brews.*, beans.name AS bean_name FROM brews JOIN beans ON beans.id = brews.bean_id ";
            String[] args = null;
            if (beanId > 0) {
                sql += "WHERE bean_id = ? ";
                args = new String[]{String.valueOf(beanId)};
            }
            sql += "ORDER BY date DESC, brew_time DESC, brews.id DESC LIMIT " + limit;
            Cursor c = getReadableDatabase().rawQuery(sql, args);
            List<Brew> out = new ArrayList<>();
            while (c.moveToNext()) out.add(readBrew(c));
            c.close();
            return out;
        }

        List<Brew> recentBrews(int days) {
            LocalDate since = LocalDate.now().minusDays(Math.max(1, days) - 1);
            Cursor c = getReadableDatabase().rawQuery(
                    "SELECT brews.*, beans.name AS bean_name FROM brews JOIN beans ON beans.id = brews.bean_id WHERE date >= ? ORDER BY date DESC, brew_time DESC, brews.id DESC LIMIT 50",
                    new String[]{since.toString()});
            List<Brew> out = new ArrayList<>();
            while (c.moveToNext()) out.add(readBrew(c));
            c.close();
            return out;
        }

        Brew bestBrew(long beanId) {
            Cursor c = getReadableDatabase().rawQuery("SELECT brews.*, beans.name AS bean_name FROM brews JOIN beans ON beans.id = brews.bean_id WHERE bean_id = ? ORDER BY score DESC LIMIT 1", new String[]{String.valueOf(beanId)});
            Brew b = null;
            if (c.moveToFirst()) b = readBrew(c);
            c.close();
            return b;
        }

        Brew bestBrewForBeans(long[] beanIds) {
            String where = inClause(beanIds);
            Cursor c = getReadableDatabase().rawQuery("SELECT brews.*, beans.name AS bean_name FROM brews JOIN beans ON beans.id = brews.bean_id WHERE bean_id IN " + where + " ORDER BY score DESC LIMIT 1", null);
            Brew b = null;
            if (c.moveToFirst()) b = readBrew(c);
            c.close();
            return b;
        }

        List<Brew> brewsForBeans(int limit, long[] beanIds) {
            Cursor c = getReadableDatabase().rawQuery("SELECT brews.*, beans.name AS bean_name FROM brews JOIN beans ON beans.id = brews.bean_id WHERE bean_id IN " + inClause(beanIds) + " ORDER BY date DESC, brew_time DESC, brews.id DESC LIMIT " + limit, null);
            List<Brew> out = new ArrayList<>();
            while (c.moveToNext()) out.add(readBrew(c));
            c.close();
            return out;
        }

        List<RecipeStock> recipeStockSummaries() {
            double totalRemaining = 0;
            Cursor total = getReadableDatabase().rawQuery("SELECT COALESCE(SUM(remaining_gram),0) FROM beans", null);
            if (total.moveToFirst()) totalRemaining = total.getDouble(0);
            total.close();

            List<RecipeStock> out = new ArrayList<>();
            Cursor c = getReadableDatabase().rawQuery("SELECT method, dose, score FROM brews WHERE dose > 0 ORDER BY method, score DESC, date DESC, brew_time DESC, id DESC", null);
            List<String> seen = new ArrayList<>();
            while (c.moveToNext()) {
                String method = clean(c.getString(c.getColumnIndexOrThrow("method")), "手冲");
                if (seen.contains(method)) continue;
                seen.add(method);
                RecipeStock summary = new RecipeStock();
                summary.method = method;
                summary.cups = Math.max(0, (int) Math.floor(totalRemaining / c.getDouble(c.getColumnIndexOrThrow("dose"))));
                out.add(summary);
            }
            c.close();

            if (out.isEmpty()) {
                RecipeStock summary = new RecipeStock();
                summary.method = "手冲";
                summary.cups = Math.max(0, (int) Math.floor(totalRemaining / 15));
                out.add(summary);
            }
            Collections.sort(out, Comparator.comparingInt((RecipeStock r) -> r.cups).reversed());
            return out;
        }

        Stats stats() {
            Stats s = new Stats();
            Cursor c = getReadableDatabase().rawQuery("SELECT COALESCE(SUM(remaining_gram),0) FROM beans", null);
            if (c.moveToFirst()) s.totalRemaining = c.getDouble(0);
            c.close();
            c = getReadableDatabase().rawQuery("SELECT COUNT(*), COALESCE(AVG(score),0), COALESCE(SUM(CASE WHEN date >= ? THEN dose ELSE 0 END),0) FROM brews", new String[]{LocalDate.now().withDayOfMonth(1).toString()});
            if (c.moveToFirst()) {
                s.brewCount = c.getInt(0);
                s.avgScore = c.getDouble(1);
                s.monthUsed = c.getDouble(2);
            }
            c.close();
            return s;
        }

        double consumptionSince(LocalDate since) {
            String sql = "SELECT COALESCE(SUM(dose),0) FROM brews";
            String[] args = null;
            if (since != null) {
                sql += " WHERE date >= ?";
                args = new String[]{since.toString()};
            }
            Cursor c = getReadableDatabase().rawQuery(sql, args);
            double total = 0;
            if (c.moveToFirst()) total = c.getDouble(0);
            c.close();
            return total;
        }

        List<ChartItem> dailyConsumption(int days) {
            List<ChartItem> out = new ArrayList<>();
            LocalDate start = LocalDate.now().minusDays(Math.max(1, days) - 1);
            for (int i = 0; i < days; i++) {
                LocalDate day = start.plusDays(i);
                out.add(new ChartItem(String.format(Locale.CHINA, "%02d/%02d", day.getMonthValue(), day.getDayOfMonth()), 0));
            }
            Cursor c = getReadableDatabase().rawQuery(
                    "SELECT date, COALESCE(SUM(dose),0) FROM brews WHERE date >= ? GROUP BY date ORDER BY date",
                    new String[]{start.toString()});
            while (c.moveToNext()) {
                String date = c.getString(0);
                double value = c.getDouble(1);
                LocalDate day = parse(date);
                String label = String.format(Locale.CHINA, "%02d/%02d", day.getMonthValue(), day.getDayOfMonth());
                for (ChartItem item : out) if (item.label.equals(label)) item.value = value;
            }
            c.close();
            return out;
        }

        List<ChartItem> methodCounts() {
            List<ChartItem> out = new ArrayList<>();
            Cursor c = getReadableDatabase().rawQuery("SELECT method, COUNT(*) FROM brews GROUP BY method ORDER BY COUNT(*) DESC, method LIMIT 6", null);
            while (c.moveToNext()) out.add(new ChartItem(clean(c.getString(0), "未记录"), c.getInt(1)));
            c.close();
            return out;
        }

        List<ChartItem> roastLevelCounts() {
            List<ChartItem> out = new ArrayList<>();
            Cursor c = getReadableDatabase().rawQuery("SELECT roast_level, COUNT(*) FROM beans GROUP BY roast_level ORDER BY COUNT(*) DESC, roast_level LIMIT 6", null);
            while (c.moveToNext()) out.add(new ChartItem(clean(c.getString(0), "未知烘焙"), c.getInt(1)));
            c.close();
            return out;
        }

        List<ChartItem> beanTypeCounts() {
            List<ChartItem> out = new ArrayList<>();
            Cursor c = getReadableDatabase().rawQuery("SELECT bean_type, COUNT(*) FROM beans GROUP BY bean_type ORDER BY COUNT(*) DESC, bean_type LIMIT 6", null);
            while (c.moveToNext()) out.add(new ChartItem(shortBeanType(clean(c.getString(0), DEFAULT_BEAN_TYPE)), c.getInt(1)));
            c.close();
            return out;
        }

        List<ChartItem> priceBuckets() {
            double[] buckets = new double[5];
            Cursor c = getReadableDatabase().rawQuery("SELECT price, total_gram FROM beans", null);
            while (c.moveToNext()) {
                double total = c.getDouble(1);
                double unitPrice = total <= 0 ? 0 : c.getDouble(0) / total;
                if (unitPrice < 0.2) buckets[0]++;
                else if (unitPrice < 0.5) buckets[1]++;
                else if (unitPrice < 1.0) buckets[2]++;
                else if (unitPrice <= 2.0) buckets[3]++;
                else buckets[4]++;
            }
            c.close();
            List<ChartItem> out = new ArrayList<>();
            out.add(new ChartItem("< ¥0.20/g", buckets[0]));
            out.add(new ChartItem("¥0.20-0.49/g", buckets[1]));
            out.add(new ChartItem("¥0.50-0.99/g", buckets[2]));
            out.add(new ChartItem("¥1.00-2.00/g", buckets[3]));
            out.add(new ChartItem("> ¥2.00/g", buckets[4]));
            return out;
        }

        List<ChartItem> monthlyPurchaseSpend() {
            List<ChartItem> out = new ArrayList<>();
            Cursor c = getReadableDatabase().rawQuery(
                    "SELECT substr(purchase_date,1,7), COALESCE(SUM(price),0) FROM beans " +
                            "WHERE purchase_date IS NOT NULL AND length(purchase_date) >= 7 " +
                            "GROUP BY substr(purchase_date,1,7) ORDER BY substr(purchase_date,1,7) DESC LIMIT 6", null);
            while (c.moveToNext()) out.add(new ChartItem(c.getString(0), c.getDouble(1)));
            c.close();
            Collections.reverse(out);
            return out;
        }

        List<ChartItem> originCounts() {
            List<ChartItem> out = new ArrayList<>();
            Cursor c = getReadableDatabase().rawQuery("SELECT origin FROM beans", null);
            while (c.moveToNext()) {
                String[] origins = clean(c.getString(0), "未知产区").split("[,，、;；/&]+");
                for (String origin : origins) {
                    String name = origin.trim();
                    if (name.isEmpty()) continue;
                    ChartItem found = null;
                    for (ChartItem item : out) if (item.label.equals(name)) { found = item; break; }
                    if (found == null) out.add(new ChartItem(name, 1));
                    else found.value++;
                }
            }
            c.close();
            Collections.sort(out, (a, b) -> Double.compare(b.value, a.value));
            if (out.size() > 8) return new ArrayList<>(out.subList(0, 8));
            return out;
        }

        LocalDate lastBrewDate(long[] beanIds) {
            Cursor c = getReadableDatabase().rawQuery("SELECT MAX(date) FROM brews WHERE bean_id IN " + inClause(beanIds), null);
            LocalDate result = LocalDate.now();
            if (c.moveToFirst()) result = parse(c.getString(0));
            c.close();
            return result;
        }

        List<ChartItem> hourCounts() {
            List<ChartItem> out = new ArrayList<>();
            int[] counts = new int[24];
            Cursor c = getReadableDatabase().rawQuery("SELECT brew_time FROM brews WHERE brew_time IS NOT NULL AND brew_time != ''", null);
            while (c.moveToNext()) {
                try {
                    String raw = cleanBrewTime(c.getString(0));
                    int hour = Integer.parseInt(raw.substring(0, 2));
                    if (hour >= 0 && hour < 24) counts[hour]++;
                } catch (Exception ignored) {
                }
            }
            c.close();
            for (int hour = 0; hour < 24; hour += 3) {
                int total = 0;
                for (int i = hour; i < hour + 3; i++) total += counts[i];
                out.add(new ChartItem(String.format(Locale.CHINA, "%02d-%02d", hour, hour + 2), total));
            }
            return out;
        }

        private String shortBeanType(String raw) {
            String value = clean(raw, DEFAULT_BEAN_TYPE);
            return value.replaceAll("\\s*/\\s*[A-Za-z][A-Za-z .'-]*", "").trim();
        }

        List<ChartItem> scoreBuckets() {
            double[] buckets = new double[5];
            Cursor c = getReadableDatabase().rawQuery("SELECT score FROM brews", null);
            while (c.moveToNext()) {
                double score = c.getDouble(0);
                int index;
                if (score < 2) index = 0;
                else if (score < 3) index = 1;
                else if (score < 4) index = 2;
                else if (score < 4.5) index = 3;
                else index = 4;
                buckets[index]++;
            }
            c.close();
            List<ChartItem> out = new ArrayList<>();
            out.add(new ChartItem("<2", buckets[0]));
            out.add(new ChartItem("2-3", buckets[1]));
            out.add(new ChartItem("3-4", buckets[2]));
            out.add(new ChartItem("4-4.5", buckets[3]));
            out.add(new ChartItem("4.5+", buckets[4]));
            return out;
        }

        double totalConsumedFromStock() {
            Cursor c = getReadableDatabase().rawQuery("SELECT COALESCE(SUM(MAX(total_gram - remaining_gram, 0)),0) FROM beans", null);
            double total = 0;
            if (c.moveToFirst()) total = c.getDouble(0);
            c.close();
            return total;
        }

        List<String> rebuyCandidates() {
            Cursor c = getReadableDatabase().rawQuery("SELECT beans.name, COUNT(*), AVG(score) FROM brews JOIN beans ON beans.id = brews.bean_id GROUP BY bean_id HAVING AVG(score) >= 4 ORDER BY AVG(score) DESC, COUNT(*) DESC LIMIT 5", null);
            List<String> out = new ArrayList<>();
            while (c.moveToNext()) out.add(c.getString(0) + " · " + c.getInt(1) + "杯 · 平均 " + String.format(Locale.CHINA, "%.1f", c.getDouble(2)) + "分");
            c.close();
            if (out.isEmpty()) out.add("还没有足够记录，评分 4 分以上会进入这里");
            return out;
        }

        List<String> alerts() {
            List<String> out = new ArrayList<>();
            LocalDate now = LocalDate.now();
            for (Bean b : beans()) {
                if (b.remainingGram <= 0) continue;
                long roastDays = ChronoUnit.DAYS.between(parse(b.roastDate), now);
                long bestDaysLeft = ChronoUnit.DAYS.between(now, parse(clean(b.bestBeforeDate, parse(b.roastDate).plusDays(45).toString())));
                if (roastDays == 7) out.add(b.name + " 养豆完成，今天可以开始认真喝");
                if (bestDaysLeft < 0) out.add(b.name + " 已过赏味期限，建议优先消耗");
                else if (bestDaysLeft <= 7) out.add(b.name + " 距离赏味期限还剩 " + bestDaysLeft + " 天");
                if (!isBlank(b.openDate)) {
                    long openDays = ChronoUnit.DAYS.between(parse(b.openDate), now);
                    if (openDays > 30) out.add(b.name + " 开封超过 30 天，香气可能开始下降");
                }
                if (b.remainingGram < 45) out.add(b.name + " 低库存，约剩 " + Math.round(b.remainingGram / 15f) + " 杯");
            }
            return out;
        }

        private List<Bean> queryBeans(String sql) {
            Cursor c = getReadableDatabase().rawQuery(sql, null);
            List<Bean> out = new ArrayList<>();
            while (c.moveToNext()) out.add(readBean(c));
            c.close();
            return out;
        }

        private String inClause(long[] ids) {
            if (ids == null || ids.length == 0) return "(-1)";
            StringBuilder out = new StringBuilder("(");
            for (int i = 0; i < ids.length; i++) {
                if (i > 0) out.append(",");
                out.append(ids[i]);
            }
            out.append(")");
            return out.toString();
        }

        private Bean readBean(Cursor c) {
            Bean b = new Bean();
            b.id = c.getLong(c.getColumnIndexOrThrow("id"));
            b.name = c.getString(c.getColumnIndexOrThrow("name"));
            b.roaster = c.getString(c.getColumnIndexOrThrow("roaster"));
            b.beanType = clean(c.getString(c.getColumnIndexOrThrow("bean_type")), DEFAULT_BEAN_TYPE);
            b.isBlend = c.getInt(c.getColumnIndexOrThrow("is_blend")) == 1;
            b.origin = c.getString(c.getColumnIndexOrThrow("origin"));
            b.process = c.getString(c.getColumnIndexOrThrow("process"));
            b.roastLevel = c.getString(c.getColumnIndexOrThrow("roast_level"));
            b.flavorTags = c.getString(c.getColumnIndexOrThrow("flavor_tags"));
            b.blendDetails = cleanOptional(c.getString(c.getColumnIndexOrThrow("blend_details")));
            b.imageUris = cleanOptional(c.getString(c.getColumnIndexOrThrow("image_uris")));
            b.packageImageUris = cleanOptional(c.getString(c.getColumnIndexOrThrow("package_image_uris")));
            b.beanImageUris = cleanOptional(c.getString(c.getColumnIndexOrThrow("bean_image_uris")));
            b.totalGram = c.getDouble(c.getColumnIndexOrThrow("total_gram"));
            b.remainingGram = c.getDouble(c.getColumnIndexOrThrow("remaining_gram"));
            b.price = c.getDouble(c.getColumnIndexOrThrow("price"));
            b.purchaseDate = c.getString(c.getColumnIndexOrThrow("purchase_date"));
            b.roastDate = c.getString(c.getColumnIndexOrThrow("roast_date"));
            b.openDate = cleanOptional(c.getString(c.getColumnIndexOrThrow("open_date")));
            b.bestBeforeDate = clean(c.getString(c.getColumnIndexOrThrow("best_before_date")), parse(b.roastDate).plusDays(45).toString());
            return b;
        }

        private Brew readBrew(Cursor c) {
            Brew b = new Brew();
            b.id = c.getLong(c.getColumnIndexOrThrow("id"));
            b.beanId = c.getLong(c.getColumnIndexOrThrow("bean_id"));
            b.beanName = c.getString(c.getColumnIndexOrThrow("bean_name"));
            b.date = c.getString(c.getColumnIndexOrThrow("date"));
            b.brewTime = cleanOptional(c.getString(c.getColumnIndexOrThrow("brew_time")));
            b.method = c.getString(c.getColumnIndexOrThrow("method"));
            b.dose = c.getDouble(c.getColumnIndexOrThrow("dose"));
            b.water = c.getDouble(c.getColumnIndexOrThrow("water"));
            b.grind = c.getString(c.getColumnIndexOrThrow("grind"));
            b.temp = c.getInt(c.getColumnIndexOrThrow("temp"));
            b.time = c.getString(c.getColumnIndexOrThrow("time"));
            b.score = c.getDouble(c.getColumnIndexOrThrow("score"));
            b.note = c.getString(c.getColumnIndexOrThrow("note"));
            b.imageUris = cleanOptional(c.getString(c.getColumnIndexOrThrow("image_uris")));
            return b;
        }

        private String clean(String raw, String fallback) {
            String s = raw == null ? "" : raw.trim();
            return s.isEmpty() ? fallback : s;
        }

        private String cleanOptional(String raw) {
            return raw == null ? "" : raw.trim();
        }

        private LocalDate parse(String raw) {
            try {
                return LocalDate.parse(raw);
            } catch (Exception e) {
                return LocalDate.now();
            }
        }
    }
}
