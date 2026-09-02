package ps.reso.instaeclipse;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import ps.reso.instaeclipse.utils.core.CommonUtils;
import ps.reso.instaeclipse.utils.feature.FeatureHealthReport;

public class VerifyHooksActivity extends AppCompatActivity {

    private static final long ATTEMPT_TIMEOUT_MS = 4500L;
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 700L;

    private RecyclerView recycler;
    private HookAdapter adapter;
    private TextView statusView;
    private TextView okCountView, brokenCountView, disabledCountView;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger loadGeneration = new AtomicInteger(0);
    private Runnable pendingTimeout;
    private Runnable pendingRetry;
    private String targetPackage;
    private int attempt;

    private final BroadcastReceiver replyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!CommonUtils.ACTION_FEATURE_STATUS_REPLY.equals(intent.getAction())) return;
            int gen = loadGeneration.get();
            cancelTimeout();
            cancelRetry();

            String err = intent.getStringExtra(CommonUtils.EXTRA_FEATURE_STATUS_ERROR);
            if (err != null) {
                if (attempt < MAX_ATTEMPTS) {
                    scheduleRetry(gen);
                    return;
                }
                showMessage(getString(R.string.verify_hooks_error_format, err));
                return;
            }
            String json = intent.getStringExtra(CommonUtils.EXTRA_FEATURE_STATUS_JSON);
            if (json == null || json.isEmpty()) {
                if (attempt < MAX_ATTEMPTS) {
                    scheduleRetry(gen);
                    return;
                }
                showMessage(getString(R.string.verify_hooks_empty_reply));
                return;
            }
            List<HookRow> rows = parseRows(json);
            if (gen != loadGeneration.get()) return;
            if (rows.isEmpty()) {
                if (attempt < MAX_ATTEMPTS) {
                    scheduleRetry(gen);
                    return;
                }
                showMessage(getString(R.string.verify_hooks_empty_reply));
            } else {
                showRows(rows);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_hooks);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        recycler = findViewById(R.id.verify_hooks_list);
        statusView = findViewById(R.id.verify_hooks_status);
        okCountView = findViewById(R.id.verify_hooks_ok_count);
        brokenCountView = findViewById(R.id.verify_hooks_broken_count);
        disabledCountView = findViewById(R.id.verify_hooks_disabled_count);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HookAdapter();
        recycler.setAdapter(adapter);

        MaterialButton btnRunTest = findViewById(R.id.verify_hooks_run_test);
        btnRunTest.setOnClickListener(v -> runTest());
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(CommonUtils.ACTION_FEATURE_STATUS_REPLY);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(replyReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            ContextCompat.registerReceiver(this, replyReceiver, filter, ContextCompat.RECEIVER_EXPORTED);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        runTest();
    }

    @Override
    protected void onStop() {
        super.onStop();
        cancelTimeout();
        cancelRetry();
        try {
            unregisterReceiver(replyReceiver);
        } catch (Throwable ignored) {}
    }

    private void runTest() {
        int gen = loadGeneration.incrementAndGet();
        cancelTimeout();
        cancelRetry();
        attempt = 0;
        targetPackage = findInstagramPackage();
        if (targetPackage == null) {
            showMessage(getString(R.string.verify_hooks_no_target));
            return;
        }
        sendRequest(gen);
    }

    private void sendRequest(int gen) {
        attempt++;
        if (gen != loadGeneration.get()) return;
        showMessage(attempt <= 1
                ? getString(R.string.verify_hooks_loading)
                : getString(R.string.verify_hooks_retrying, attempt, MAX_ATTEMPTS));

        Intent request = new Intent(CommonUtils.ACTION_REQUEST_FEATURE_STATUS);
        request.setPackage(targetPackage);
        request.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        sendBroadcast(request);
        scheduleTimeout(gen);
    }

    private void scheduleTimeout(int gen) {
        pendingTimeout = () -> {
            if (gen != loadGeneration.get()) return;
            if (attempt < MAX_ATTEMPTS) {
                scheduleRetry(gen);
                return;
            }
            showMessage(getString(R.string.verify_hooks_timeout));
        };
        mainHandler.postDelayed(pendingTimeout, ATTEMPT_TIMEOUT_MS);
    }

    private void scheduleRetry(int gen) {
        cancelTimeout();
        cancelRetry();
        pendingRetry = () -> {
            if (gen != loadGeneration.get()) return;
            sendRequest(gen);
        };
        mainHandler.postDelayed(pendingRetry, RETRY_DELAY_MS);
    }

    private void cancelTimeout() {
        if (pendingTimeout != null) {
            mainHandler.removeCallbacks(pendingTimeout);
            pendingTimeout = null;
        }
    }

    private void cancelRetry() {
        if (pendingRetry != null) {
            mainHandler.removeCallbacks(pendingRetry);
            pendingRetry = null;
        }
    }

    private String findInstagramPackage() {
        PackageManager pm = getPackageManager();
        for (String pkg : CommonUtils.SUPPORTED_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0);
                return pkg;
            } catch (PackageManager.NameNotFoundException ignored) {}
        }
        return null;
    }

    private List<HookRow> parseRows(String json) {
        List<HookRow> rows = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                rows.add(new HookRow(
                        o.optString("key", ""),
                        o.optString("label", o.optString("key", "")),
                        o.optString("state", FeatureHealthReport.STATE_DISABLED),
                        o.optString("error", "")));
            }
        } catch (Throwable ignored) {}

        rows.sort(Comparator.comparingInt(VerifyHooksActivity::stateOrder).thenComparing(r -> r.label));
        return rows;
    }

    private static int stateOrder(HookRow row) {
        if (FeatureHealthReport.STATE_BROKEN.equals(row.state)) return 0;
        if (FeatureHealthReport.STATE_OK.equals(row.state)) return 1;
        return 2;
    }

    private void showMessage(String msg) {
        statusView.setVisibility(View.VISIBLE);
        statusView.setText(msg);
        recycler.setVisibility(View.GONE);
        adapter.setRows(new ArrayList<>());
        updateCounts(new ArrayList<>());
    }

    private void showRows(List<HookRow> rows) {
        statusView.setVisibility(View.GONE);
        recycler.setVisibility(View.VISIBLE);
        adapter.setRows(rows);
        updateCounts(rows);
    }

    private void updateCounts(List<HookRow> rows) {
        int ok = 0, broken = 0, disabled = 0;
        for (HookRow row : rows) {
            if (FeatureHealthReport.STATE_OK.equals(row.state)) ok++;
            else if (FeatureHealthReport.STATE_BROKEN.equals(row.state)) broken++;
            else disabled++;
        }
        okCountView.setText(String.valueOf(ok));
        brokenCountView.setText(String.valueOf(broken));
        disabledCountView.setText(String.valueOf(disabled));
    }

    private static final class HookRow {
        final String key, label, state, error;
        HookRow(String key, String label, String state, String error) {
            this.key = key;
            this.label = label;
            this.state = state;
            this.error = error;
        }
    }

    private static final class HookRowViewHolder extends RecyclerView.ViewHolder {
        final ImageView stateIcon;
        final TextView titleView, subtitleView;
        HookRowViewHolder(View itemView) {
            super(itemView);
            stateIcon = itemView.findViewById(R.id.row_state_icon);
            titleView = itemView.findViewById(R.id.row_title);
            subtitleView = itemView.findViewById(R.id.row_subtitle);
        }
    }

    private final class HookAdapter extends RecyclerView.Adapter<HookRowViewHolder> {
        private List<HookRow> rows = new ArrayList<>();

        void setRows(List<HookRow> rows) {
            this.rows = rows;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public HookRowViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_verify_hook_row, parent, false);
            return new HookRowViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull HookRowViewHolder holder, int position) {
            HookRow row = rows.get(position);
            holder.titleView.setText(row.label);

            int iconRes;
            int tint;
            String subtitle;
            if (FeatureHealthReport.STATE_OK.equals(row.state)) {
                iconRes = R.drawable.ic_check_circle;
                tint = ContextCompat.getColor(holder.itemView.getContext(), R.color.dark_green);
                subtitle = holder.itemView.getContext().getString(R.string.verify_hooks_state_ok);
            } else if (FeatureHealthReport.STATE_BROKEN.equals(row.state)) {
                iconRes = R.drawable.ic_cancel;
                tint = ContextCompat.getColor(holder.itemView.getContext(), R.color.dark_red);
                subtitle = !row.error.isEmpty() ? row.error : holder.itemView.getContext().getString(R.string.verify_hooks_state_broken);
            } else {
                iconRes = R.drawable.ic_cancel;
                tint = ContextCompat.getColor(holder.itemView.getContext(), R.color.gray);
                subtitle = holder.itemView.getContext().getString(R.string.verify_hooks_state_disabled);
            }

            android.graphics.drawable.Drawable icon = ContextCompat.getDrawable(holder.itemView.getContext(), iconRes);
            if (icon != null) {
                icon = icon.mutate();
                icon.setColorFilter(new PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN));
                holder.stateIcon.setImageDrawable(icon);
            }
            holder.subtitleView.setText(subtitle);
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }
}
