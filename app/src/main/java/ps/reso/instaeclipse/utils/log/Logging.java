package ps.reso.instaeclipse.utils.log;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/**
 * Persistent, size-bounded ring buffer of log lines, used to power the in-app log viewer
 * (LoggingFragment). Runs independently in whichever process calls init() — Instagram's
 * hooked process and the companion app each keep their own buffer/file, since a hooked
 * process can't directly read another process's memory.
 *
 * Consecutive identical lines are collapsed into a single "(×N)" entry to avoid the buffer
 * filling up with noise from a hook that logs the same thing on every call.
 */
public final class Logging {

    private static final long FLUSH_DELAY_MS = 750;
    // Broadcast extras ride the Binder transaction buffer (~1MB total, shared across all
    // in-flight transactions in the process), and Java strings are UTF-16 (2 bytes/char), so
    // this must stay well under half a million chars or the reply broadcast triggers
    // TransactionTooLargeException -> the receiving process gets killed with
    // CannotDeliverBroadcastException instead of ever seeing the intent.
    private static final int IPC_MAX_CHARS = 150000;
    private static final long MAX_FILE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_LINES = 10000;

    private static final Object LOCK = new Object();
    private static final ArrayDeque<String> LINES = new ArrayDeque<>(MAX_LINES + 16);
    private static final ThreadLocal<SimpleDateFormat> TS =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US));

    private static boolean initialized;
    private static File logFile;
    private static HandlerThread ioThread;
    private static Handler ioHandler;
    private static boolean dirtyFile;
    private static String lastBody;
    private static String lastBodyTime;
    private static int lastBodyRepeat = 1;

    private static final Runnable REWRITE_RUNNABLE = Logging::rewriteFileRunnable;

    public enum Filter {
        ALL,
        ISSUES,
        ERRORS
    }

    private Logging() {}

    public static void init(Context context) {
        init(context, "instaeclipse_logging.log");
    }

    public static void init(Context context, String filename) {
        synchronized (LOCK) {
            if (initialized) return;
            logFile = new File(context.getFilesDir(), filename);
            ensureIoThread();
            loadFromFileLocked();
            initialized = true;
        }
    }

    private static void ensureIoThread() {
        if (ioThread != null) return;
        ioThread = new HandlerThread("InstaEclipse-Logging-IO");
        ioThread.start();
        ioHandler = new Handler(ioThread.getLooper());
    }

    private static void loadFromFileLocked() {
        if (logFile == null || !logFile.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                trimLinesIfNeeded();
                LINES.addLast(line);
            }
        } catch (Throwable ignored) {}
    }

    public static void append(String line) {
        if (line == null || line.isEmpty()) return;
        String time = TS.get().format(new Date());
        synchronized (LOCK) {
            if (!initialized) return;
            if (line.equals(lastBody) && !LINES.isEmpty()) {
                lastBodyRepeat++;
                LINES.pollLast();
                LINES.addLast(lastBodyTime + " " + line + " (×" + lastBodyRepeat + ")");
                dirtyFile = true;
                scheduleFlushLocked();
                return;
            }
            String entry = time + " " + line;
            boolean hadDirty = dirtyFile;
            dirtyFile = false;
            lastBody = line;
            lastBodyRepeat = 1;
            lastBodyTime = time;
            trimLinesIfNeeded();
            LINES.addLast(entry);
            postIoWrite(hadDirty, entry);
        }
    }

    private static void trimLinesIfNeeded() {
        while (LINES.size() >= MAX_LINES) LINES.pollFirst();
    }

    private static void scheduleFlushLocked() {
        if (ioHandler == null) return;
        ioHandler.removeCallbacks(REWRITE_RUNNABLE);
        ioHandler.postDelayed(REWRITE_RUNNABLE, FLUSH_DELAY_MS);
    }

    private static void rewriteFileRunnable() {
        synchronized (LOCK) {
            dirtyFile = false;
            rewriteFileLocked();
        }
    }

    private static void postIoWrite(boolean rewriteFirst, String entry) {
        if (ioHandler == null) return;
        ioHandler.post(() -> {
            synchronized (LOCK) {
                if (rewriteFirst) rewriteFileLocked();
                else appendLineToFileLocked(entry);
            }
        });
    }

    private static void appendLineToFileLocked(String entry) {
        if (logFile == null) return;
        try {
            if (logFile.exists() && logFile.length() > MAX_FILE_BYTES) {
                rewriteFileLocked();
                return;
            }
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(logFile, true))) {
                bw.write(entry);
                bw.newLine();
            }
        } catch (Throwable ignored) {}
    }

    private static void rewriteFileLocked() {
        if (logFile == null) return;
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(logFile, false))) {
            for (String s : LINES) {
                bw.write(s);
                bw.newLine();
            }
        } catch (Throwable ignored) {}
    }

    public static void clear() {
        synchronized (LOCK) {
            LINES.clear();
            lastBody = null;
            lastBodyRepeat = 1;
            lastBodyTime = null;
            dirtyFile = false;
        }
        if (ioHandler != null) {
            ioHandler.removeCallbacks(REWRITE_RUNNABLE);
            ioHandler.post(() -> {
                synchronized (LOCK) {
                    if (logFile != null && logFile.exists()) {
                        try { logFile.delete(); } catch (Throwable ignored) {}
                    }
                }
            });
        }
    }

    public static String getSnapshot() {
        synchronized (LOCK) {
            return buildSnapshot(false);
        }
    }

    public static String getSnapshotForIpc() {
        synchronized (LOCK) {
            return buildSnapshot(true);
        }
    }

    public static String filterText(String text, Filter filter) {
        if (text == null || text.isEmpty() || filter == Filter.ALL) return text;
        StringBuilder out = new StringBuilder(text.length() / 2);
        int start = 0;
        while (start <= text.length()) {
            int nl = text.indexOf('\n', start);
            String line = nl < 0 ? text.substring(start) : text.substring(start, nl);
            if (keepLine(line, filter)) {
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

    private static boolean keepLine(String line, Filter filter) {
        if (line.isEmpty()) return true;
        if (line.startsWith("===") || line.startsWith("[")) return true;
        String upper = line.toUpperCase(Locale.US);
        boolean error = upper.contains("EXCEPTION")
                || upper.contains("ERROR")
                || upper.contains(" FATAL")
                || upper.contains(" BROKEN ")
                || line.contains("❌")
                || upper.contains("INSTALL_ERROR");
        boolean warn = line.contains("⚠️")
                || upper.contains("FAILED")
                || upper.contains("NOT_CONFIRMED")
                || upper.contains(" ISSUE ");
        if (filter == Filter.ERRORS) return error;
        return error || warn;
    }

    private static String buildSnapshot(boolean forIpc) {
        if (LINES.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(LINES.size() * 96);
        for (String s : LINES) sb.append(s).append('\n');
        String snap = sb.toString();
        if (!forIpc || snap.length() <= IPC_MAX_CHARS) return snap;
        return "(truncated — older lines omitted)\n\n" + snap.substring(snap.length() - IPC_MAX_CHARS + 40);
    }
}
