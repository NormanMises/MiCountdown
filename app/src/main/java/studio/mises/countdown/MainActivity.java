package studio.mises.countdown;

import android.Manifest;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int CALENDAR_PERMISSION_REQUEST = 1;
    static final String EXTRA_EVENT_TITLE = "studio.mises.countdown.EVENT_TITLE";
    static final String EXTRA_EVENT_DATE = "studio.mises.countdown.EVENT_DATE";

    @Override
    protected void onResume() {
        super.onResume();
        show();
        CountdownWidget.refresh(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    private void show() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        controls.setPadding(padding, padding, padding, padding);
        scroll.addView(controls);
        setContentView(scroll);

        TextView heading = new TextView(this);
        heading.setText("最近倒数日");
        heading.setTextSize(28);
        controls.addView(heading);

        boolean authorized = CountdownWidget.hasCalendarPermission(this);
        String title = getIntent().getStringExtra(EXTRA_EVENT_TITLE);
        if (authorized && title != null) {
            TextView event = new TextView(this);
            event.setTextSize(23);
            event.setPadding(0, padding, 0, 0);
            event.setText(title);
            event.setTextIsSelectable(true);
            controls.addView(event);

            TextView date = new TextView(this);
            date.setText(getIntent().getStringExtra(EXTRA_EVENT_DATE));
            date.setPadding(0, 0, 0, padding);
            controls.addView(date);
        }

        TextView status = new TextView(this);
        status.setText(authorized
                ? "自动显示今天或未来最近的小米倒数日。"
                : "授权后即可读取小米日历中的倒数日。");
        controls.addView(status);

        if (!authorized) {
            addButton(controls, "授权读取日历", () -> requestPermissions(
                    new String[]{Manifest.permission.READ_CALENDAR}, CALENDAR_PERMISSION_REQUEST));
        }
        addButton(controls, "添加到桌面", () -> {
            AppWidgetManager manager = AppWidgetManager.getInstance(this);
            if (manager.isRequestPinAppWidgetSupported()) {
                manager.requestPinAppWidget(new ComponentName(this, CountdownWidget.class), null, null);
            } else {
                Toast.makeText(this, "请从桌面小部件列表添加", Toast.LENGTH_LONG).show();
            }
        });
        addButton(controls, "刷新小部件", () -> {
            CountdownWidget.refresh(this);
            Toast.makeText(this, "已请求刷新", Toast.LENGTH_SHORT).show();
        });
    }

    private void addButton(LinearLayout controls, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(view -> action.run());
        controls.addView(button);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == CALENDAR_PERMISSION_REQUEST) {
            show();
            CountdownWidget.refresh(this);
        }
    }
}
