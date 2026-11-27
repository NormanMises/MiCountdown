package studio.mises.countdown;

import android.Manifest;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CountdownWidget extends AppWidgetProvider {
    static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final DateTimeFormatter THIS_YEAR =
            DateTimeFormatter.ofPattern("M月d日 EEE", Locale.CHINA);
    private static final DateTimeFormatter OTHER_YEAR =
            DateTimeFormatter.ofPattern("yyyy年M月d日 EEE", Locale.CHINA);

    static void refresh(Context context) {
        Context app = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        ComponentName provider = new ComponentName(app, CountdownWidget.class);
        int[] ids = manager.getAppWidgetIds(provider);
        if (ids.length != 0) {
            WORKER.execute(() -> render(app, manager, ids));
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        Context app = context.getApplicationContext();
        PendingResult pending = goAsync();
        WORKER.execute(() -> {
            try {
                render(app, manager, ids);
            } finally {
                if (pending != null) {
                    pending.finish();
                }
            }
        });
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
            int widgetId, Bundle options) {
        onUpdate(context, manager, new int[]{widgetId});
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            onUpdate(context, manager,
                    manager.getAppWidgetIds(new ComponentName(context, CountdownWidget.class)));
        }
    }

    static boolean hasCalendarPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** What the card shows; shared by the widget and the app screen. */
    record Snapshot(String header, String title, CharSequence days, String hint) {
        boolean hasEvent() {
            return days != null;
        }
    }

    /** Reads the calendar; call off the main thread. */
    static Snapshot load(Context context) {
        if (!hasCalendarPermission(context)) {
            return new Snapshot("最近倒数日", "读取小米日历里的倒数日", null, "点按授权");
        }
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        try {
            CalendarData.Occurrence nearest =
                    CalendarData.nearest(context.getContentResolver(), today, zone);
            if (nearest == null) {
                return new Snapshot("最近倒数日", "暂无未来的倒数日", null, "在小米日历中添加");
            }
            return new Snapshot(formatDate(nearest.date(), today), nearest.title(),
                    formatDays(ChronoUnit.DAYS.between(today, nearest.date()),
                            context.getColor(R.color.widget_accent)),
                    null);
        } catch (RuntimeException error) {
            Log.w("MiCountdown", "Calendar read failed", error);
            return new Snapshot("最近倒数日", "读取失败", null, "点按重试");
        }
    }

    private static void render(Context context, AppWidgetManager manager, int[] ids) {
        if (ids.length == 0) {
            return;
        }

        Snapshot snapshot = load(context);
        boolean night = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int layout = night ? R.layout.widget_night : R.layout.widget;
        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openApp = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), layout);
            views.setTextViewText(R.id.date, snapshot.header());
            views.setTextViewText(R.id.title, snapshot.title());
            // Without an event, a short call to action takes the number's place
            // instead of a giant placeholder dash.
            if (snapshot.hasEvent()) {
                views.setTextViewText(R.id.days, snapshot.days());
                views.setViewVisibility(R.id.days, View.VISIBLE);
                views.setViewVisibility(R.id.hint, View.GONE);
            } else {
                views.setTextViewText(R.id.hint, snapshot.hint());
                views.setViewVisibility(R.id.days, View.GONE);
                views.setViewVisibility(R.id.hint, View.VISIBLE);
            }
            views.setOnClickPendingIntent(R.id.card, openApp);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Bundle options = manager.getAppWidgetOptions(id);
                fitCard(views, options);
            }
            manager.updateAppWidget(id, views);
        }
    }

    private static String formatDate(LocalDate date, LocalDate today) {
        return (date.getYear() == today.getYear() ? THIS_YEAR : OTHER_YEAR).format(date);
    }

    static CharSequence formatDays(long remaining, int accent) {
        if (remaining == 0) {
            SpannableString todayText = new SpannableString("就是今天");
            todayText.setSpan(new ForegroundColorSpan(accent), 0, todayText.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return todayText;
        }
        String number = Long.toString(remaining);
        SpannableString days = new SpannableString(number + "\u00a0天");
        int unit = number.length();
        days.setSpan(new RelativeSizeSpan(0.42f), unit, days.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        days.setSpan(new ForegroundColorSpan(accent), unit, days.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return days;
    }

    private static void fitCard(RemoteViews views, Bundle options) {
        int width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        int height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
        if (width <= 0 || height <= 6) {
            return;
        }

        // Leave a little room to lower the card on launchers whose cells
        // center Android widgets above their own native cards.
        int side = Math.min(width, height - 6);
        int offset = Math.min(8, (height - side) / 2);
        views.setViewLayoutWidth(R.id.card, side, TypedValue.COMPLEX_UNIT_DIP);
        views.setViewLayoutHeight(R.id.card, side, TypedValue.COMPLEX_UNIT_DIP);
        views.setViewLayoutMargin(R.id.card, RemoteViews.MARGIN_TOP, offset,
                TypedValue.COMPLEX_UNIT_DIP);
    }
}
