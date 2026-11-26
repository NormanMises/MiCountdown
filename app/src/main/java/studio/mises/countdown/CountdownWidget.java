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
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.util.TypedValue;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CountdownWidget extends AppWidgetProvider {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

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

        String title = "最近倒数日";
        CharSequence days = "—";
        String date = "点击授权日历";
        boolean hasEvent = false;
        if (hasCalendarPermission(context)) {
            ZoneId zone = ZoneId.systemDefault();
            LocalDate today = LocalDate.now(zone);
            try {
                CalendarData.Occurrence nearest =
                        CalendarData.nearest(context.getContentResolver(), today, zone);
                if (nearest == null) {
                    date = "暂无未来倒数日";
                } else {
                    hasEvent = true;
                    title = nearest.title();
                    long remaining = ChronoUnit.DAYS.between(today, nearest.date());
                    days = formatDays(remaining);
                    date = nearest.date().toString();
                }
            } catch (RuntimeException error) {
                Log.w("MiCountdown", "Calendar read failed", error);
                date = "读取失败，点击重试";
            }
        }

        boolean night = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int layout = night ? R.layout.widget_night : R.layout.widget;
        Intent detail = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (hasEvent) {
            detail.putExtra(MainActivity.EXTRA_EVENT_TITLE, title);
            detail.putExtra(MainActivity.EXTRA_EVENT_DATE, date);
        }
        PendingIntent openApp = PendingIntent.getActivity(context, 0, detail,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), layout);
            views.setTextViewText(R.id.title, title);
            views.setTextViewText(R.id.days, days);
            views.setTextViewText(R.id.date, date);
            views.setOnClickPendingIntent(R.id.card, openApp);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Bundle options = manager.getAppWidgetOptions(id);
                fitCard(views, options);
            }
            manager.updateAppWidget(id, views);
        }
    }

    private static CharSequence formatDays(long remaining) {
        if (remaining == 0) {
            return "今天";
        }
        String number = Long.toString(remaining);
        SpannableString days = new SpannableString(number + "\u00a0天");
        days.setSpan(new RelativeSizeSpan(0.48f), number.length(), days.length(),
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
