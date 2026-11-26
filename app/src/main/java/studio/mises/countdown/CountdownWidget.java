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
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CountdownWidget extends AppWidgetProvider {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
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

    private static void render(Context context, AppWidgetManager manager, int[] ids) {
        if (ids.length == 0) {
            return;
        }

        int accent = context.getColor(R.color.widget_accent);
        String header = "最近倒数日";
        String title = "点击授权读取日历";
        CharSequence days = "—";
        boolean hasEvent = false;
        if (hasCalendarPermission(context)) {
            ZoneId zone = ZoneId.systemDefault();
            LocalDate today = LocalDate.now(zone);
            try {
                CalendarData.Occurrence nearest =
                        CalendarData.nearest(context.getContentResolver(), today, zone);
                if (nearest == null) {
                    title = "暂无未来的倒数日";
                } else {
                    hasEvent = true;
                    title = nearest.title();
                    header = formatDate(nearest.date(), today);
                    days = formatDays(ChronoUnit.DAYS.between(today, nearest.date()), accent);
                }
            } catch (RuntimeException error) {
                Log.w("MiCountdown", "Calendar read failed", error);
                title = "读取失败，点击重试";
            }
        }

        boolean night = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int layout = night ? R.layout.widget_night : R.layout.widget;
        Intent detail = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (hasEvent) {
            detail.putExtra(MainActivity.EXTRA_EVENT_TITLE, title);
            detail.putExtra(MainActivity.EXTRA_EVENT_DATE, header);
        }
        PendingIntent openApp = PendingIntent.getActivity(context, 0, detail,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), layout);
            views.setTextViewText(R.id.title, title);
            views.setTextViewText(R.id.days, days);
            views.setTextViewText(R.id.date, header);
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

    private static CharSequence formatDays(long remaining, int accent) {
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
