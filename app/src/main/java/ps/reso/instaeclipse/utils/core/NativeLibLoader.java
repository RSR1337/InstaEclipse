package ps.reso.instaeclipse.utils.core;

import android.annotation.SuppressLint;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class NativeLibLoader {

    private NativeLibLoader() {}

    public static String normalizeModulePath(String path) {
        if (path == null || path.isEmpty()) return path;
        String p = path;
        if (p.startsWith("jar:file:")) {
            p = p.substring("jar:file:".length());
            int bang = p.indexOf('!');
            if (bang >= 0) p = p.substring(0, bang);
        } else if (p.startsWith("file:")) {
            p = p.substring("file:".length());
        }
        return p;
    }

    public static String resolveModulePath(String current) {
        String normalized = normalizeModulePath(current);
        if (normalized != null && new File(normalized).isFile()) return normalized;
        try {
            Class<?> xposedInit = Class.forName("de.robv.android.xposed.XposedInit");
            Object loaded = xposedInit.getMethod("getLoadedModules").invoke(null);
            if (loaded instanceof Map) {
                Object path = ((Map<?, ?>) loaded).get(CommonUtils.MY_PACKAGE_NAME);
                if (path instanceof Optional) {
                    Optional<?> opt = (Optional<?>) path;
                    if (opt.isPresent() && opt.get() instanceof String) {
                        return normalizeModulePath((String) opt.get());
                    }
                } else if (path instanceof String) {
                    return normalizeModulePath((String) path);
                }
            }
        } catch (Throwable ignored) {}
        return normalized;
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    public static void loadDexKit(String modulePath, String hostDataDir) {
        String apkPath = resolveModulePath(modulePath);
        if (apkPath == null || apkPath.isEmpty()) {
            throw new UnsatisfiedLinkError("module path not set");
        }
        UnsatisfiedLinkError last = null;

        try {
            loadViaClassLoader("dexkit");
            return;
        } catch (UnsatisfiedLinkError e) {
            last = e;
        } catch (Throwable t) {
            last = new UnsatisfiedLinkError(String.valueOf(t.getMessage()));
        }

        String moduleDir;
        int slash = apkPath.lastIndexOf('/');
        if (slash > 0) {
            moduleDir = apkPath.substring(0, slash);
            for (String abi : Build.SUPPORTED_ABIS) {
                try {
                    System.load(moduleDir + "/lib/" + abi + "/libdexkit.so");
                    return;
                } catch (UnsatisfiedLinkError e) {
                    last = e;
                }
            }
        }

        try {
            System.load(legacyExtractedLibPath(apkPath));
            return;
        } catch (UnsatisfiedLinkError e) {
            last = e;
        }

        for (String abi : Build.SUPPORTED_ABIS) {
            try {
                System.load(apkPath + "!/lib/" + abi + "/libdexkit.so");
                return;
            } catch (UnsatisfiedLinkError e) {
                last = e;
            }
        }

        if (hostDataDir != null && !hostDataDir.isEmpty()) {
            try {
                File extracted = extractFromApk(apkPath, hostDataDir);
                System.load(extracted.getAbsolutePath());
                return;
            } catch (UnsatisfiedLinkError e) {
                last = e;
            } catch (Throwable t) {
                last = new UnsatisfiedLinkError(String.valueOf(t.getMessage()));
            }
        }

        throw last != null ? last : new UnsatisfiedLinkError("libdexkit.so not found");
    }

    private static void loadViaClassLoader(String libName) throws Exception {
        ClassLoader cl = NativeLibLoader.class.getClassLoader();
        if (cl == null) throw new UnsatisfiedLinkError("no classloader");
        Method loadLibrary0 = Runtime.class.getDeclaredMethod("loadLibrary0", ClassLoader.class, String.class);
        loadLibrary0.setAccessible(true);
        loadLibrary0.invoke(Runtime.getRuntime(), cl, libName);
    }

    private static String legacyExtractedLibPath(String apkPath) {
        int slash = apkPath.lastIndexOf('/');
        if (slash <= 0) throw new UnsatisfiedLinkError("bad module path");
        String abi = Build.SUPPORTED_ABIS[0];
        String abiFolder;
        if (abi.equalsIgnoreCase("arm64-v8a")) abiFolder = "arm64";
        else if (abi.equalsIgnoreCase("armeabi-v7a") || abi.equalsIgnoreCase("armeabi"))
            abiFolder = "arm";
        else if (abi.equalsIgnoreCase("x86")) abiFolder = "x86";
        else if (abi.equalsIgnoreCase("x86_64")) abiFolder = "x86_64";
        else abiFolder = abi;
        return apkPath.substring(0, slash) + "/lib/" + abiFolder + "/libdexkit.so";
    }

    private static File extractFromApk(String apkPath, String hostDataDir) throws Exception {
        File dir = new File(hostDataDir, "code_cache/instaeclipse");
        if (!dir.exists() && !dir.mkdirs()) {
            dir = new File(hostDataDir, "files/instaeclipse_nativelib");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new UnsatisfiedLinkError("cannot create native lib dir");
            }
        }
        File apk = new File(apkPath);
        File out = new File(dir, "libdexkit_" + apk.lastModified() + "_" + Build.SUPPORTED_ABIS[0] + ".so");
        if (out.exists() && out.length() > 0) {
            out.setReadable(true, false);
            out.setExecutable(true, false);
            return out;
        }
        try (ZipFile zf = new ZipFile(apkPath)) {
            ZipEntry entry = null;
            for (String abi : Build.SUPPORTED_ABIS) {
                entry = zf.getEntry("lib/" + abi + "/libdexkit.so");
                if (entry != null) break;
            }
            if (entry == null) throw new UnsatisfiedLinkError("libdexkit.so not in module apk");
            try (InputStream in = zf.getInputStream(entry); FileOutputStream fos = new FileOutputStream(out)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    fos.write(buf, 0, n);
                }
            }
        }
        out.setReadable(true, false);
        out.setExecutable(true, false);
        return out;
    }
}
