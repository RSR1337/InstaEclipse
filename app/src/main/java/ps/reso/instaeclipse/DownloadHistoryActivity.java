package ps.reso.instaeclipse;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import ps.reso.instaeclipse.utils.core.CommonUtils;

public class DownloadHistoryActivity extends AppCompatActivity {

    private static final long REPLY_TIMEOUT_MS = 3500;

    private RecyclerView recycler;
    private HistoryAdapter adapter;
    private TextView statusView;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger loadGeneration = new AtomicInteger(0);
    private Runnable pendingTimeout;

    private final BroadcastReceiver replyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!CommonUtils.ACTION_DOWNLOAD_HISTORY_REPLY.equals(intent.getAction())) return;
            int gen = loadGeneration.get();
            cancelTimeout();

            String err = intent.getStringExtra(CommonUtils.EXTRA_DOWNLOAD_HISTORY_ERROR);
            if (err != null) {
                showMessage(getString(R.string.download_history_error_format, err));
                return;
            }
            String json = intent.getStringExtra(CommonUtils.EXTRA_DOWNLOAD_HISTORY_JSON);
            if (json == null || json.isEmpty()) {
                showMessage(getString(R.string.download_history_empty));
                return;
            }
            List<HistoryRow> rows = parseRows(json);
            if (gen != loadGeneration.get()) return;
            if (rows.isEmpty()) {
                showMessage(getString(R.string.download_history_empty));
            } else {
                showRows(rows);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_history);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        recycler = findViewById(R.id.download_history_list);
        statusView = findViewById(R.id.download_history_status);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter();
        recycler.setAdapter(adapter);

        MaterialButton btnClear = findViewById(R.id.download_history_clear);
        btnClear.setOnClickListener(v -> confirmClear());
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(CommonUtils.ACTION_DOWNLOAD_HISTORY_REPLY);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(replyReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            ContextCompat.registerReceiver(this, replyReceiver, filter, ContextCompat.RECEIVER_EXPORTED);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHistory();
    }

    @Override
    protected void onStop() {
        super.onStop();
        cancelTimeout();
        try {
            unregisterReceiver(replyReceiver);
        } catch (Throwable ignored) {}
    }

    private void loadHistory() {
        int gen = loadGeneration.incrementAndGet();
        cancelTimeout();
        showMessage(getString(R.string.download_history_loading));

        String pkg = findInstagramPackage();
        if (pkg == null) {
            showMessage(getString(R.string.download_history_no_target));
            return;
        }
        Intent request = new Intent(CommonUtils.ACTION_REQUEST_DOWNLOAD_HISTORY);
        request.setPackage(pkg);
        sendBroadcast(request);
        scheduleTimeout(gen);
    }

    private void scheduleTimeout(int gen) {
        pendingTimeout = () -> {
            if (gen != loadGeneration.get()) return;
            showMessage(getString(R.string.download_history_timeout));
        };
        mainHandler.postDelayed(pendingTimeout, REPLY_TIMEOUT_MS);
    }

    private void cancelTimeout() {
        if (pendingTimeout != null) {
            mainHandler.removeCallbacks(pendingTimeout);
            pendingTimeout = null;
        }
    }

    private void confirmClear() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.download_history_title)
                .setMessage(R.string.download_history_clear_confirm)
                .setPositiveButton(R.string.download_history_clear, (d, w) -> clearHistory())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void clearHistory() {
        String pkg = findInstagramPackage();
        if (pkg != null) {
            Intent clear = new Intent(CommonUtils.ACTION_CLEAR_DOWNLOAD_HISTORY);
            clear.setPackage(pkg);
            sendBroadcast(clear);
        }
        loadHistory();
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

    private List<HistoryRow> parseRows(String json) {
        List<HistoryRow> rows = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                rows.add(new HistoryRow(
                        o.optString("type", "post"),
                        o.optString("username", "unknown"),
                        o.optString("filename", ""),
                        o.optString("time", "")));
            }
        } catch (Throwable ignored) {}
        return rows;
    }

    private void showMessage(String msg) {
        statusView.setVisibility(View.VISIBLE);
        statusView.setText(msg);
        recycler.setVisibility(View.GONE);
        adapter.setRows(new ArrayList<>());
    }

    private void showRows(List<HistoryRow> rows) {
        statusView.setVisibility(View.GONE);
        recycler.setVisibility(View.VISIBLE);
        adapter.setRows(rows);
    }

    private static final class HistoryRow {
        final String type, username, filename, time;
        HistoryRow(String type, String username, String filename, String time) {
            this.type = type;
            this.username = username;
            this.filename = filename;
            this.time = time;
        }
    }

    private static final class HistoryRowViewHolder extends RecyclerView.ViewHolder {
        final TextView filenameView, subtitleView;
        HistoryRowViewHolder(View itemView) {
            super(itemView);
            filenameView = itemView.findViewById(R.id.row_filename);
            subtitleView = itemView.findViewById(R.id.row_subtitle);
        }
    }

    private static final class HistoryAdapter extends RecyclerView.Adapter<HistoryRowViewHolder> {
        private List<HistoryRow> rows = new ArrayList<>();

        void setRows(List<HistoryRow> rows) {
            this.rows = rows;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public HistoryRowViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_download_history_row, parent, false);
            return new HistoryRowViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull HistoryRowViewHolder holder, int position) {
            HistoryRow row = rows.get(position);
            holder.filenameView.setText(row.filename);
            String type = capitalize(row.type);
            holder.subtitleView.setText(holder.itemView.getContext().getString(
                    R.string.download_history_subtitle_format, type, row.username, row.time));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        private static String capitalize(String s) {
            if (s == null || s.isEmpty()) return s;
            return Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }
    }
}
