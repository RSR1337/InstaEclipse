package ps.reso.instaeclipse.utils.history;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class DownloadHistory {

    private static final int MAX_ENTRIES = 300;
    private static final Object LOCK = new Object();
    private static final ArrayDeque<JSONObject> ENTRIES = new ArrayDeque<>(MAX_ENTRIES + 16);
    private static final ThreadLocal<SimpleDateFormat> TS =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US));

    private static File historyFile;
    private static HandlerThread ioThread;
    private static Handler ioHandler;

    private DownloadHistory() {}

    public static void init(Context context) {
        synchronized (LOCK) {
            if (historyFile != null) return;
            historyFile = new File(context.getFilesDir(), "instaeclipse_downloads.log");
            ioThread = new HandlerThread("InstaEclipse-DownloadHistory-IO");
            ioThread.start();
            ioHandler = new Handler(ioThread.getLooper());
            loadFromFileLocked();
        }
    }

    private static void loadFromFileLocked() {
        if (historyFile == null || !historyFile.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(historyFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                try {
                    JSONObject obj = new JSONObject(line);
                    while (ENTRIES.size() >= MAX_ENTRIES) ENTRIES.pollFirst();
                    ENTRIES.addLast(obj);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    public static void record(String type, String username, String filename) {
        JSONObject entry = new JSONObject();
        try {
            entry.put("time", TS.get().format(new Date()));
            entry.put("type", type);
            entry.put("username", (username == null || username.isEmpty()) ? "unknown" : username);
            entry.put("filename", filename);
        } catch (Throwable ignored) {
            return;
        }
        synchronized (LOCK) {
            while (ENTRIES.size() >= MAX_ENTRIES) ENTRIES.pollFirst();
            ENTRIES.addLast(entry);
            if (ioHandler != null) ioHandler.post(() -> appendToFile(entry));
        }
    }

    private static void appendToFile(JSONObject entry) {
        synchronized (LOCK) {
            if (historyFile == null) return;
            try (FileWriter fw = new FileWriter(historyFile, true)) {
                fw.write(entry.toString());
                fw.write("\n");
            } catch (Throwable ignored) {}
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            ENTRIES.clear();
        }
        if (ioHandler != null) {
            ioHandler.post(() -> {
                synchronized (LOCK) {
                    if (historyFile != null && historyFile.exists()) {
                        try { historyFile.delete(); } catch (Throwable ignored) {}
                    }
                }
            });
        }
    }

    public static String snapshotJson() {
        synchronized (LOCK) {
            JSONArray arr = new JSONArray();
            List<JSONObject> list = new ArrayList<>(ENTRIES);
            Collections.reverse(list);
            for (JSONObject entry : list) arr.put(entry);
            return arr.toString();
        }
    }
}
