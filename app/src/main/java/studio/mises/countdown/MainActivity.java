package studio.mises.countdown;

import android.Manifest;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
            runOnUiThread(() -> show(snapshot));
        });
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
