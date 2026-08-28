package ps.reso.instaeclipse.fragments;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputEditText;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.utils.core.CommonUtils;
import ps.reso.instaeclipse.utils.log.Logging;

public class LoggingFragment extends Fragment {

    private static final long INSTAGRAM_REPLY_TIMEOUT_MS = 4000;
    private static final int MAX_DISPLAY_CHARS = 100000;
    private static final int NEAR_BOTTOM_SLOP_PX = 48;

    private TextView contentView;
    private TextView lineCountView;
    private NestedScrollView scrollView;
    private ImageButton searchClear;
    private Runnable pendingTimeout;
    private String companionSection = "";
    private String rawCombined = "";
    private String searchQuery = "";
    private Logging.Filter activeFilter = Logging.Filter.ALL;

    private final ExecutorService loadExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger loadGeneration = new AtomicInteger(0);

    private final BroadcastReceiver logReplyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!CommonUtils.ACTION_LOGS_REPLY.equals(intent.getAction()) || contentView == null) return;
            int gen = loadGeneration.get();
            cancelInstagramTimeout();
            loadExecutor.execute(() -> {
                String instagram = formatInstagramSection(intent);
                String combined = joinSections(instagram, companionSection);
                mainHandler.post(() -> {
                    if (contentView == null || gen != loadGeneration.get()) return;
                    applyDisplay(combined);
                });
            });
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_logging, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        contentView = view.findViewById(R.id.logging_content);
        lineCountView = view.findViewById(R.id.logging_line_count);
        scrollView = view.findViewById(R.id.logging_scroll);
        searchClear = view.findViewById(R.id.logging_search_clear);
        view.findViewById(R.id.logging_copy).setOnClickListener(v -> copyLogs());
        view.findViewById(R.id.logging_clear).setOnClickListener(v -> clearLogs());
        view.findViewById(R.id.logging_share).setOnClickListener(v -> shareLogs());

        ChipGroup filterGroup = view.findViewById(R.id.logging_filter_group);
        filterGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.logging_filter_issues) activeFilter = Logging.Filter.ISSUES;
            else if (id == R.id.logging_filter_errors) activeFilter = Logging.Filter.ERRORS;
            else activeFilter = Logging.Filter.ALL;
            renderFiltered(true, false);
        });

        TextInputEditText searchInput = view.findViewById(R.id.logging_search_input);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchClear.setVisibility(s == null || s.length() == 0 ? View.GONE : View.VISIBLE);
                searchQuery = s == null ? "" : s.toString();
                renderFiltered(true, false);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                return true;
            }
            return false;
        });
        searchClear.setOnClickListener(v -> searchInput.setText(""));
    }

    @Override
    public void onResume() {
        super.onResume();
        loadLogs();
    }

    @Override
    public void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(CommonUtils.ACTION_LOGS_REPLY);
        if (Build.VERSION.SDK_INT >= 33) {
            requireContext().registerReceiver(logReplyReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            ContextCompat.registerReceiver(requireContext(), logReplyReceiver, filter, ContextCompat.RECEIVER_EXPORTED);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        cancelInstagramTimeout();
        try {
            requireContext().unregisterReceiver(logReplyReceiver);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelInstagramTimeout();
        contentView = null;
        lineCountView = null;
        scrollView = null;
        searchClear = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        loadExecutor.shutdownNow();
    }

    private void loadLogs() {
        Context ctx = getContext();
        if (ctx == null || contentView == null) return;
        int gen = loadGeneration.incrementAndGet();
        cancelInstagramTimeout();
        contentView.setText(R.string.logging_loading);
        loadExecutor.execute(() -> {
            String companion = formatCompanionSection(ctx);
            String pkg = findInstagramPackage(ctx);
            mainHandler.post(() -> {
                if (contentView == null || gen != loadGeneration.get()) return;
                companionSection = companion;
                if (pkg == null) {
                    applyDisplay(joinSections(formatInstagramUnavailableSection(), companionSection));
                    return;
                }
                Intent request = new Intent(CommonUtils.ACTION_REQUEST_LOGS);
                request.setPackage(pkg);
                ctx.sendBroadcast(request);
                scheduleInstagramTimeout(gen);
            });
        });
    }

    private void scheduleInstagramTimeout(int gen) {
        pendingTimeout = () -> {
            if (contentView == null || gen != loadGeneration.get()) return;
            String ig = sectionHeader(getString(R.string.logging_instagram), null)
                    + getString(R.string.logging_instagram_timeout) + "\n";
            applyDisplay(joinSections(ig, companionSection));
        };
        mainHandler.postDelayed(pendingTimeout, INSTAGRAM_REPLY_TIMEOUT_MS);
    }

    private void cancelInstagramTimeout() {
        if (pendingTimeout != null) {
            mainHandler.removeCallbacks(pendingTimeout);
            pendingTimeout = null;
        }
    }

    private String formatCompanionSection(Context ctx) {
        String snap = Logging.getSnapshot().trim();
        if (snap.isEmpty()) return "";
        String header = sectionHeader(getString(R.string.logging_companion), ctx.getPackageName());
        return header + snap;
    }

    private static String joinSections(String first, String second) {
        if (second == null || second.isEmpty()) return first;
        if (first == null || first.isEmpty()) return second;
        return first + "\n\n" + second;
    }

    private String formatInstagramUnavailableSection() {
        return sectionHeader(getString(R.string.logging_instagram), null) + getString(R.string.logging_no_target);
    }

    private String formatInstagramSection(Intent intent) {
        String src = intent.getStringExtra(CommonUtils.EXTRA_LOG_SOURCE);
        String header = sectionHeader(getString(R.string.logging_instagram), src);
        String err = intent.getStringExtra(CommonUtils.EXTRA_LOG_ERROR);
        if (err != null) return header + err;
        String body = intent.getStringExtra(CommonUtils.EXTRA_LOG_TEXT);
        if (body == null || body.trim().isEmpty()) return header + getString(R.string.logging_empty_reply);
        if (body.length() > MAX_DISPLAY_CHARS) {
            body = body.substring(0, MAX_DISPLAY_CHARS) + "\n\n[Truncated - logs too large to display]";
        }
        return header + body.trim();
    }

    private static String sectionHeader(String title, String packageName) {
        String header = "=== " + title + " ===\n";
        if (packageName != null) header = header + "[" + packageName + "]\n";
        return header + "\n";
    }

    private void applyDisplay(String text) {
        boolean pinnedToBottom = rawCombined.isEmpty() || isScrolledNearBottom();
        rawCombined = text == null ? "" : text;
        renderFiltered(false, pinnedToBottom);
    }

    private boolean isScrolledNearBottom() {
        NestedScrollView sv = scrollView;
        if (sv == null) return true;
        View child = sv.getChildAt(0);
        if (child == null) return true;
        int remaining = child.getBottom() - (sv.getHeight() + sv.getScrollY());
        return remaining <= NEAR_BOTTOM_SLOP_PX;
    }

    private String applySearchFilter(String text) {
        String query = searchQuery.trim();
        if (query.isEmpty() || text.isEmpty()) return text;
        StringBuilder out = new StringBuilder(text.length() / 2);
        int start = 0;
        while (start <= text.length()) {
            int nl = text.indexOf('\n', start);
            String line = nl < 0 ? text.substring(start) : text.substring(start, nl);
            if (line.startsWith("===") || line.startsWith("[") || containsIgnoreCase(line, query)) {
                out.append(line).append('\n');
            }
            if (nl < 0) break;
            start = nl + 1;
        }
        int len = out.length();
        while (len > 0 && out.charAt(len - 1) == '\n') {
            out.setLength(--len);
        }
        return out.toString();
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase(java.util.Locale.ROOT).contains(needle.toLowerCase(java.util.Locale.ROOT));
    }

    private void renderFiltered(boolean scrollToTop, boolean autoScrollToBottom) {
        if (contentView == null) return;
        String levelFiltered = Logging.filterText(rawCombined, activeFilter);
        String searched = applySearchFilter(levelFiltered);
        String display;
        boolean placeholder = false;
        if (searched.isEmpty() && !levelFiltered.isEmpty() && !searchQuery.trim().isEmpty()) {
            display = getString(R.string.logging_search_empty);
            placeholder = true;
        } else if (levelFiltered.isEmpty() && !rawCombined.isEmpty() && activeFilter != Logging.Filter.ALL) {
            display = getString(R.string.logging_filter_empty);
            placeholder = true;
        } else {
            display = searched;
        }
        contentView.setText(styledLogText(contentView, display));
        if (lineCountView != null) {
            int shown = placeholder || searched.isEmpty() ? 0 : countLines(searched);
            int total = rawCombined.isEmpty() ? 0 : countLines(rawCombined);
            lineCountView.setVisibility(View.VISIBLE);
            if (activeFilter == Logging.Filter.ALL && searchQuery.trim().isEmpty()) {
                lineCountView.setText(getString(R.string.logging_lines_format, shown));
            } else {
                lineCountView.setText(getString(R.string.logging_lines_filtered_format, shown, total));
            }
        }
        if (scrollToTop && scrollView != null) {
            scrollView.post(() -> {
                if (scrollView != null) scrollView.scrollTo(0, 0);
            });
        } else if (autoScrollToBottom && scrollView != null) {
            scrollView.post(() -> {
                if (scrollView != null) scrollView.fullScroll(View.FOCUS_DOWN);
            });
        }
    }

    private static int countLines(String text) {
        if (text.isEmpty()) return 0;
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') lines++;
        }
        return lines;
    }

    private CharSequence styledLogText(View anchor, String text) {
        if (text == null || text.isEmpty()) return text;
        int errorColor = MaterialColors.getColor(anchor, com.google.android.material.R.attr.colorError);
        int warningColor = ContextCompat.getColor(anchor.getContext(), R.color.warning_yellow);
        int mutedColor = MaterialColors.getColor(anchor, com.google.android.material.R.attr.colorOnSurfaceVariant);
        int accentColor = MaterialColors.getColor(anchor, com.google.android.material.R.attr.colorPrimary);
        SpannableStringBuilder builder = new SpannableStringBuilder();
        int start = 0;
        while (start <= text.length()) {
            int nl = text.indexOf('\n', start);
            String line = nl < 0 ? text.substring(start) : text.substring(start, nl);
            int spanStart = builder.length();
            builder.append(line);
            int spanEnd = builder.length();
            if (line.startsWith("===") || line.startsWith("[")) {
                builder.setSpan(new StyleSpan(Typeface.BOLD), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new ForegroundColorSpan(accentColor), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (line.contains("❌") || containsIgnoreCase(line, "exception") || containsIgnoreCase(line, "error")) {
                builder.setSpan(new ForegroundColorSpan(errorColor), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (line.contains("⚠️") || containsIgnoreCase(line, "failed")) {
                builder.setSpan(new ForegroundColorSpan(warningColor), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (line.contains("✅")) {
                builder.setSpan(new ForegroundColorSpan(mutedColor), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (nl < 0) break;
            builder.append('\n');
            start = nl + 1;
        }
        return builder;
    }

    private static String findInstagramPackage(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        for (String pkg : CommonUtils.SUPPORTED_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0);
                return pkg;
            } catch (PackageManager.NameNotFoundException ignored) {}
        }
        return null;
    }

    private void clearLogs() {
        Context ctx = getContext();
        if (ctx == null) return;
        String pkg = findInstagramPackage(ctx);
        if (pkg != null) {
            Intent clear = new Intent(CommonUtils.ACTION_CLEAR_LOGS);
            clear.setPackage(pkg);
            ctx.sendBroadcast(clear);
        }
        Logging.clear();
        loadLogs();
    }

    private boolean isBlankDisplay() {
        if (contentView == null) return true;
        String text = contentView.getText().toString();
        return text.isEmpty()
                || getString(R.string.logging_placeholder).contentEquals(text)
                || getString(R.string.logging_loading).contentEquals(text)
                || getString(R.string.logging_filter_empty).contentEquals(text)
                || getString(R.string.logging_search_empty).contentEquals(text);
    }

    private void copyLogs() {
        if (isBlankDisplay()) {
            Toast.makeText(requireContext(), R.string.logging_empty_reply, Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("logs", contentView.getText().toString()));
        Toast.makeText(requireContext(), R.string.logging_copied, Toast.LENGTH_SHORT).show();
    }

    private void shareLogs() {
        if (isBlankDisplay()) {
            Toast.makeText(requireContext(), R.string.logging_empty_reply, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, contentView.getText().toString());
        startActivity(Intent.createChooser(share, getString(R.string.logging_share_title)));
    }
}
