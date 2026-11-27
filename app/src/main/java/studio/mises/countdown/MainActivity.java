package studio.mises.countdown;

import android.Manifest;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class MainActivity extends Activity {
    private static final int CALENDAR_PERMISSION_REQUEST = 1;
    /** The in-app card is the 168dp widget layout drawn at this scale. */
    private static final float CARD_SCALE = 1.5f;

    private TextView date;
    private TextView title;
    private TextView days;
    private TextView hint;
    private View permissionNote;
    private Button primary;
    private Button secondary;
    private TextView installed;
    private LinearLayout secondaryRow;
    private View update;
    private TextView updateTitle;
    private TextView updateDetail;
    private Button updateDownload;
    private View listSection;
    private TextView listHeading;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        drawBehindSystemBars();

        View card = findViewById(R.id.card);
        card.setScaleX(CARD_SCALE);
        card.setScaleY(CARD_SCALE);
        card.setElevation(4 * getResources().getDisplayMetrics().density);
        card.setForeground(null); // Not tappable here.
        date = findViewById(R.id.date);
        title = findViewById(R.id.title);
        days = findViewById(R.id.days);
        hint = findViewById(R.id.hint);
        permissionNote = findViewById(R.id.permission_note);
        primary = findViewById(R.id.primary);
        secondary = findViewById(R.id.secondary);
        installed = findViewById(R.id.installed);
        secondaryRow = findViewById(R.id.secondary_row);
        update = findViewById(R.id.update);
        updateTitle = findViewById(R.id.update_title);
        updateDetail = findViewById(R.id.update_detail);
        updateDownload = findViewById(R.id.update_download);
        listSection = findViewById(R.id.list_section);
        listHeading = findViewById(R.id.list_heading);
        list = findViewById(R.id.list);

        TextView footnote = findViewById(R.id.footnote);
        footnote.setText("版本 " + UpdateChecker.currentVersion(this)
                + " · 打开本页时会刷新小部件；之后系统约每 30 分钟自动刷新一次。");
    }

    @Override
    protected void onResume() {
        super.onResume();
        update();
        CountdownWidget.refresh(this);
    }

    /** Updates the existing views in place; the layout is built once in onCreate. */
    private void update() {
        boolean authorized = CountdownWidget.hasCalendarPermission(this);
        boolean onHomeScreen = AppWidgetManager.getInstance(this)
                .getAppWidgetIds(new ComponentName(this, CountdownWidget.class)).length > 0;

        permissionNote.setVisibility(authorized ? View.GONE : View.VISIBLE);
        installed.setVisibility(authorized && onHomeScreen ? View.VISIBLE : View.GONE);
        secondaryRow.setGravity(installed.getVisibility() == View.VISIBLE
                ? Gravity.CENTER_VERTICAL | Gravity.START : Gravity.CENTER);

        // One primary action per state; adding again is always available, but quieter.
        String addLabel = onHomeScreen ? "再添加一个" : "添加到桌面";
        if (!authorized) {
            showPrimary("授权读取日历", this::requestCalendar);
            showSecondary(addLabel);
        } else if (!onHomeScreen) {
            showPrimary(addLabel, this::pinWidget);
            secondary.setVisibility(View.GONE);
        } else {
            primary.setVisibility(View.GONE);
            showSecondary(addLabel);
        }

        CountdownWidget.WORKER.execute(() -> {
            CountdownWidget.Snapshot snapshot = CountdownWidget.load(this);
            List<CalendarData.Occurrence> all = loadAll();
            runOnUiThread(() -> {
                show(snapshot);
                showList(all);
            });
        });
        checkForUpdate();
    }

    /** Runs on its own thread so a slow network never delays widget rendering. */
    private void checkForUpdate() {
        new Thread(() -> {
            UpdateChecker.Release release = UpdateChecker.newerRelease(getApplicationContext());
            runOnUiThread(() -> {
                if (isDestroyed()) {
                    return;
                }
                if (release == null) {
                    update.setVisibility(View.GONE);
                    return;
                }
                updateTitle.setText("有新版本 " + release.version());
                updateDetail.setText("当前 " + UpdateChecker.currentVersion(this)
                        + "，下载后直接安装即可覆盖升级");
                updateDownload.setOnClickListener(view -> openLink(release.downloadUrl()));
                update.setVisibility(View.VISIBLE);
            });
        }, "update-check").start();
    }

    private void openLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (android.content.ActivityNotFoundException error) {
            Toast.makeText(this, "没有可以打开链接的浏览器", Toast.LENGTH_LONG).show();
        }
    }

    private void show(CountdownWidget.Snapshot snapshot) {
        date.setText(snapshot.header());
        title.setText(snapshot.title());
        if (snapshot.hasEvent()) {
            days.setText(snapshot.days());
            days.setVisibility(View.VISIBLE);
            hint.setVisibility(View.GONE);
        } else {
            days.setVisibility(View.GONE);
            hint.setVisibility(View.GONE); // The buttons below say what to do.
        }
    }

    /** Every upcoming countdown, or null when the calendar cannot be read. */
    private List<CalendarData.Occurrence> loadAll() {
        if (!CountdownWidget.hasCalendarPermission(this)) {
            return null;
        }
        ZoneId zone = ZoneId.systemDefault();
        try {
            return CalendarData.upcoming(getContentResolver(), LocalDate.now(zone), zone);
        } catch (RuntimeException error) {
            Log.w("MiCountdown", "Calendar read failed", error);
            return null;
        }
    }

    private void showList(List<CalendarData.Occurrence> all) {
        list.removeAllViews();
        // A single countdown is already the card above; a list of one adds nothing.
        if (all == null || all.size() < 2) {
            listSection.setVisibility(View.GONE);
            return;
        }
        listHeading.setText("全部倒数日 · " + all.size());
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        int accent = getColor(R.color.widget_accent);
        LayoutInflater inflater = getLayoutInflater();
        for (CalendarData.Occurrence occurrence : all) {
            View row = inflater.inflate(R.layout.item_countdown, list, false);
            ((TextView) row.findViewById(R.id.row_title)).setText(occurrence.title());
            ((TextView) row.findViewById(R.id.row_date))
                    .setText(CountdownWidget.formatDate(occurrence.date(), today));
            ((TextView) row.findViewById(R.id.row_days))
                    .setText(rowDays(ChronoUnit.DAYS.between(today, occurrence.date()), accent));
            list.addView(row);
        }
        listSection.setVisibility(View.VISIBLE);
    }

    private static CharSequence rowDays(long remaining, int accent) {
        if (remaining == 0) {
            SpannableString today = new SpannableString("今天");
            today.setSpan(new ForegroundColorSpan(accent), 0, today.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return today;
        }
        String number = Long.toString(remaining);
        SpannableString days = new SpannableString(number + "\u00a0天");
        int unit = number.length();
        days.setSpan(new RelativeSizeSpan(0.65f), unit, days.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        days.setSpan(new ForegroundColorSpan(accent), unit, days.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return days;
    }

    private void showPrimary(String label, Runnable action) {
        primary.setText(label);
        primary.setOnClickListener(view -> action.run());
        primary.setVisibility(View.VISIBLE);
    }

    private void showSecondary(String label) {
        secondary.setText(label);
        secondary.setOnClickListener(view -> pinWidget());
        secondary.setVisibility(View.VISIBLE);
    }

    private void requestCalendar() {
        requestPermissions(new String[]{Manifest.permission.READ_CALENDAR},
                CALENDAR_PERMISSION_REQUEST);
    }

    private void pinWidget() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        if (manager.isRequestPinAppWidgetSupported()) {
            manager.requestPinAppWidget(new ComponentName(this, CountdownWidget.class), null, null);
        } else {
            Toast.makeText(this, "请从桌面小部件列表添加", Toast.LENGTH_LONG).show();
        }
    }

    @SuppressWarnings("deprecation")
    private void drawBehindSystemBars() {
        View root = findViewById(R.id.root);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        int left = root.getPaddingLeft();
        int top = root.getPaddingTop();
        int right = root.getPaddingRight();
        int bottom = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int insetLeft;
            int insetTop;
            int insetRight;
            int insetBottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout());
                insetLeft = bars.left;
                insetTop = bars.top;
                insetRight = bars.right;
                insetBottom = bars.bottom;
            } else {
                insetLeft = insets.getSystemWindowInsetLeft();
                insetTop = insets.getSystemWindowInsetTop();
                insetRight = insets.getSystemWindowInsetRight();
                insetBottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(left + insetLeft, top + insetTop,
                    right + insetRight, bottom + insetBottom);
            return insets;
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == CALENDAR_PERMISSION_REQUEST) {
            update();
            CountdownWidget.refresh(this);
        }
    }
}
