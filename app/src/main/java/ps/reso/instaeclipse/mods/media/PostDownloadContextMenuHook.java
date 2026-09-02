package ps.reso.instaeclipse.mods.media;

import android.app.AndroidAppHelper;
import android.content.Context;
import android.widget.Toast;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.utils.core.DexKitCache;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.i18n.I18n;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class PostDownloadContextMenuHook {

    private static Class<?> mediaOptionEnumClass;
    private static Object   downloadOptionValue;
    private static Class<?> mediaClass;

    private static Class<?> menuCreatorClass;

    private static Method   addButtonMethod;
    private static Object   enumNormalValue;

    private static int idxEnum   = 0;
    private static int idxOption = 1;
    private static int idxSelf   = 2;
    private static int idxText   = 3;
    private static int idxList   = 4;

    private static final ThreadLocal<Boolean> sAddingDownload =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static final int PROCESSED_LIMIT = 32;
    private static final Map<Object, Boolean> processedCreators = new IdentityHashMap<>();

    public void install(DexKitBridge bridge, ClassLoader classLoader) {
        try {
            mediaClass = classLoader.loadClass("com.instagram.feed.media.Media");
        } catch (Throwable ignored) {}
        loadMediaOptionEnum(classLoader);
        findCreatorClassAndAddButtonMethod(bridge, classLoader);
        installAddButtonHook();
        installClickHandlerHook(bridge, classLoader);
        installAllowlistPatchHook(bridge, classLoader);
    }

    private static void loadMediaOptionEnum(ClassLoader cl) {
        try {
            mediaOptionEnumClass = cl.loadClass(
                    "com.instagram.feed.media.mediaoption.MediaOption$Option");
            Object[] values = (Object[]) mediaOptionEnumClass.getMethod("values").invoke(null);
            for (Object v : values) {
                if (downloadOptionValue == null && v.toString().equals("DOWNLOAD")) {
                    downloadOptionValue = v;
                }
            }
            if (downloadOptionValue == null)
                ModuleLog.line("(IE|Post) ❌ DOWNLOAD enum value not found");
        } catch (Throwable t) {
            ModuleLog.line("(IE|Post) ❌ loadMediaOptionEnum: " + t);
        }
    }

    private static void findCreatorClassAndAddButtonMethod(DexKitBridge bridge,
                                                            ClassLoader classLoader) {
        if (DexKitCache.isCacheValid()) {
            Method cached = DexKitCache.loadMethod("PostDownload_addButton", classLoader);
            if (cached != null) {
                addButtonMethod = cached;
                addButtonMethod.setAccessible(true);
                menuCreatorClass = cached.getDeclaringClass();
                String idxStr = DexKitCache.loadString("PostDownload_addButtonIdx");
                if (idxStr != null) {
                    String[] parts = idxStr.split(",");
                    if (parts.length == 5) {
                        try {
                            idxEnum   = Integer.parseInt(parts[0]);
                            idxOption = Integer.parseInt(parts[1]);
                            idxSelf   = Integer.parseInt(parts[2]);
                            idxText   = Integer.parseInt(parts[3]);
                            idxList   = Integer.parseInt(parts[4]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                if (idxEnum >= 0) {
                    try {
                        Class<?> btnTypeEnum = cached.getParameterTypes()[idxEnum];
                        Object[] vals = (Object[]) btnTypeEnum.getMethod("values").invoke(null);
                        Object first = null;
                        for (Object v : vals) {
                            if (first == null) first = v;
                            if (enumNormalValue == null && v.toString().equalsIgnoreCase("normal"))
                                enumNormalValue = v;
                        }
                        if (enumNormalValue == null)
                            for (Object v : vals)
                                if (v.toString().equalsIgnoreCase("action")) { enumNormalValue = v; break; }
                        if (enumNormalValue == null) enumNormalValue = first;
                    } catch (Throwable ignored) {}
                }
                return;
            }
        }

        try {
            List<ClassData> pass1 = bridge.findClass(FindClass.create()
                    .matcher(ClassMatcher.create()
                            .usingStrings("MediaOptionsOverflowMenuCreator")));

            if (pass1.isEmpty()) {
                pass1 = bridge.findClass(FindClass.create()
                        .matcher(ClassMatcher.create()
                                .usingStrings("OverflowMenuCreator")));
            }

            if (pass1.isEmpty()) {
                ModuleLog.line("(IE|Post) ❌ MediaOptionsOverflowMenuCreator class not found");
                return;
            }

            String creatorClassName = pass1.get(0).getName();
            menuCreatorClass = classLoader.loadClass(creatorClassName);

            List<MethodData> pass2 = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .declaredClass(creatorClassName)
                            .returnType("void")));

            for (MethodData md : pass2) {
                try {
                    Method m = md.getMethodInstance(classLoader);
                    if (!Modifier.isStatic(m.getModifiers())) continue;

                    Class<?>[] p = m.getParameterTypes();
                    if (p.length < 4) continue;

                    int eIdx = -1, oIdx = -1, sIdx = -1, tIdx = -1, lIdx = -1;
                    for (int i = 0; i < p.length; i++) {
                        if (mediaOptionEnumClass != null && p[i] == mediaOptionEnumClass) {
                            oIdx = i;
                        } else if (ArrayList.class.isAssignableFrom(p[i])) {
                            lIdx = i;
                        } else if (p[i] == menuCreatorClass) {
                            sIdx = i;
                        } else if (CharSequence.class.isAssignableFrom(p[i])) {
                            tIdx = i;
                        } else if (p[i].isEnum() && eIdx < 0 && oIdx < 0) {
                            eIdx = i;
                        }
                    }

                    if (oIdx < 0 || lIdx < 0) continue;

                    addButtonMethod = m;
                    addButtonMethod.setAccessible(true);
                    idxEnum   = eIdx >= 0 ? eIdx : 0;
                    idxOption = oIdx;
                    idxSelf   = sIdx >= 0 ? sIdx : 2;
                    idxText   = tIdx >= 0 ? tIdx : 3;
                    idxList   = lIdx;
                    break;
                } catch (Throwable ignored) {}
            }

            if (addButtonMethod == null) {
                ModuleLog.line("(IE|Post) ❌ addButtonMethod not found in " + creatorClassName);
                return;
            }
            DexKitCache.saveMethod("PostDownload_addButton", addButtonMethod);
            DexKitCache.saveString("PostDownload_addButtonIdx",
                    idxEnum + "," + idxOption + "," + idxSelf + "," + idxText + "," + idxList);

            Class<?> btnTypeEnumClass = addButtonMethod.getParameterTypes()[idxEnum];
            Object[] btnVals = (Object[]) btnTypeEnumClass.getMethod("values").invoke(null);
            Object firstVal = null;
            for (Object v : btnVals) {
                if (firstVal == null) firstVal = v;
                if (enumNormalValue == null && v.toString().equalsIgnoreCase("normal")) {
                    enumNormalValue = v;
                }
            }
            if (enumNormalValue == null) {
                for (Object v : btnVals) {
                    if (v.toString().equalsIgnoreCase("action")) { enumNormalValue = v; break; }
                }
            }
            if (enumNormalValue == null) enumNormalValue = firstVal;

        } catch (Throwable t) {
            ModuleLog.line("(IE|Post) ❌ findCreatorClassAndAddButtonMethod: " + t);
        }
    }

    private static void installAddButtonHook() {
        if (addButtonMethod == null || downloadOptionValue == null || enumNormalValue == null) {
            ModuleLog.line("(IE|Post) ❌ Cannot install addButton hook — prerequisites missing");
            return;
        }

        XposedBridge.hookMethod(addButtonMethod, new XC_MethodHook() {

            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (Boolean.TRUE.equals(sAddingDownload.get())) return;
                if (!FeatureFlags.enablePostDownload && !FeatureFlags.enableReelDownload) return;
                if (param.args[idxOption] == downloadOptionValue) param.setResult(null);
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!FeatureFlags.enablePostDownload && !FeatureFlags.enableReelDownload) return;
                if (Boolean.TRUE.equals(sAddingDownload.get())) return;
                if (param.args[idxOption] == downloadOptionValue) return;

                Object self = param.args[idxSelf];
                boolean alreadyProcessed;
                synchronized (processedCreators) {
                    alreadyProcessed = processedCreators.containsKey(self);
                    if (!alreadyProcessed) {
                        processedCreators.put(self, Boolean.TRUE);
                        if (processedCreators.size() > PROCESSED_LIMIT) {
                            processedCreators.remove(processedCreators.keySet().iterator().next());
                        }
                    }
                }
                if (alreadyProcessed) return;

                Object[] callArgs = new Object[addButtonMethod.getParameterCount()];
                System.arraycopy(param.args, 0, callArgs, 0, callArgs.length);
                callArgs[idxEnum]   = enumNormalValue;
                callArgs[idxOption] = downloadOptionValue;
                callArgs[idxText]   = I18n.t(AndroidAppHelper.currentApplication(), R.string.ig_dl_title);

                sAddingDownload.set(true);
                try {
                    addButtonMethod.invoke(null, callArgs);
                } catch (Throwable t) {
                    ModuleLog.line("(IE|Post) ❌ addButton invoke failed: " + t);
                } finally {
                    sAddingDownload.set(false);
                }
            }
        });

        FeatureStatusTracker.setHooked("PostDownload");
        ModuleLog.line("(IE|Post) ✅ Post download hook installed");
    }

    private static void installClickHandlerHook(DexKitBridge bridge, ClassLoader classLoader) {
        XC_MethodHook clickHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!FeatureFlags.enablePostDownload && !FeatureFlags.enableReelDownload) return;
                onOptionClicked(param);
            }
        };

        if (DexKitCache.isCacheValid()) {
            List<Method> cached = DexKitCache.loadMethods("PostDownload_click_v2", classLoader);
            if (cached != null && !cached.isEmpty()) {
                for (Method m : cached) XposedBridge.hookMethod(m, clickHook);
                return;
            }
        }

        try {
            String optionClassName = "com.instagram.feed.media.mediaoption.MediaOption$Option";

            List<MethodData> results = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .returnType("void")
                            .paramTypes(optionClassName)));

            List<Method> candidates = new ArrayList<>();
            for (MethodData md : results) {
                try {
                    Method m = md.getMethodInstance(classLoader);
                    if (Modifier.isStatic(m.getModifiers())) continue;
                    candidates.add(m);
                } catch (Throwable ignored) {}
            }

            boolean hasPublicCandidate = candidates.stream().anyMatch(m -> Modifier.isPublic(m.getModifiers()));

            List<Method> hooked = new ArrayList<>();
            for (Method m : candidates) {
                try {
                    if (hasPublicCandidate && Modifier.isPrivate(m.getModifiers())) continue;
                    m.setAccessible(true);
                    XposedBridge.hookMethod(m, clickHook);
                    hooked.add(m);
                } catch (Throwable t) {
                    ModuleLog.line("(IE|Post) ❌ Failed to hook click candidate: " + t);
                }
            }

            if (hooked.isEmpty()) {
                ModuleLog.line("(IE|Post) ❌ No click handler methods could be hooked");
            } else {
                DexKitCache.saveMethods("PostDownload_click_v2", hooked);
            }

        } catch (Throwable t) {
            ModuleLog.line("(IE|Post) ❌ installClickHandlerHook: " + t);
        }
    }

    private static void installAllowlistPatchHook(DexKitBridge bridge, ClassLoader classLoader) {
        XC_MethodHook allowlistHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (downloadOptionValue == null) return;
                try {
                    Object result = param.getResult();
                    if (!(result instanceof List<?> original)) return;
                    if (original.contains(downloadOptionValue)) return;
                    List<Object> patched = new ArrayList<>(original);
                    patched.add(downloadOptionValue);
                    param.setResult(patched);
                } catch (Throwable t) {
                    ModuleLog.line("(IE|Post) ❌ allowlist patch failed: " + t);
                }
            }
        };

        if (DexKitCache.isCacheValid()) {
            List<Method> cached = DexKitCache.loadMethods("PostDownload_allowlist", classLoader);
            if (cached != null && !cached.isEmpty()) {
                for (Method m : cached) {
                    XposedBridge.hookMethod(m, allowlistHook);
                }
                return;
            }
        }

        try {
            String optionClassName = "com.instagram.feed.media.mediaoption.MediaOption$Option";
            String typeDesc = "L" + optionClassName.replace('.', '/') + ";";
            String prefix = typeDesc + "->";

            List<MethodData> results = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .paramTypes("boolean")
                            .returnType("java.util.List")
                            .addUsingField(prefix + "REPORT:" + typeDesc)
                            .addUsingField(prefix + "HIDE_OPTIONS:" + typeDesc)
                            .addUsingField(prefix + "GEN_AI_INFO:" + typeDesc)));

            if (results.isEmpty()) {
                results = bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                                .paramTypes("boolean")
                                .returnType("java.util.List")
                                .addUsingField(prefix + "REPORT:" + typeDesc)
                                .addUsingField(prefix + "HIDE_OPTIONS:" + typeDesc)
                                .addUsingField(prefix + "WHY_AM_I_SEEING_THIS:" + typeDesc)));
            }

            List<MethodData> filterMethods = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .paramCount(2)
                            .returnType("java.util.List")
                            .addUsingField(prefix + "REPORT:" + typeDesc)
                            .addUsingField(prefix + "HIDE_OPTIONS:" + typeDesc)));

            if (results.isEmpty() && filterMethods.isEmpty()) {
                ModuleLog.line("(IE|Post) ⚠️ Allowlist method not found (menu may not be filtered on this build)");
                return;
            }

            List<Method> hooked = new ArrayList<>();
            for (MethodData md : results) {
                try {
                    Method target = md.getMethodInstance(classLoader);
                    target.setAccessible(true);
                    XposedBridge.hookMethod(target, allowlistHook);
                    hooked.add(target);
                    ModuleLog.line("(IE|Post) ✅ Allowlist patch hooked: " +
                            target.getDeclaringClass().getName() + "." + target.getName());
                } catch (Throwable ignored) {}
            }
            for (MethodData md : filterMethods) {
                try {
                    Method target = md.getMethodInstance(classLoader);
                    if (hooked.contains(target)) continue;
                    target.setAccessible(true);
                    XposedBridge.hookMethod(target, allowlistHook);
                    hooked.add(target);
                    ModuleLog.line("(IE|Post) ✅ Allowlist filter hooked: " +
                            target.getDeclaringClass().getName() + "." + target.getName());
                } catch (Throwable ignored) {}
            }
            if (!hooked.isEmpty()) DexKitCache.saveMethods("PostDownload_allowlist", hooked);

        } catch (Throwable t) {
            ModuleLog.line("(IE|Post) ❌ installAllowlistPatchHook: " + t);
        }
    }

    private static void onOptionClicked(XC_MethodHook.MethodHookParam param) {
        try {
            if (Boolean.TRUE.equals(sAddingDownload.get())) return;

            Object clicked = null;
            for (Object a : param.args) {
                if (a != null && mediaOptionEnumClass != null && mediaOptionEnumClass.isInstance(a)) {
                    clicked = a; break;
                }
            }
            if (clicked == null) {
                for (Object a : param.args) {
                    if (a != null && a.getClass().isEnum() && a.toString().contains("DOWNLOAD")) {
                        clicked = a; break;
                    }
                }
            }

            if (clicked == null || !clicked.toString().equals("DOWNLOAD")) return;

            param.setResult(null);

            Object thisObj = param.thisObject;

            Context ctx = findContext(thisObj);
            if (ctx == null) {
                ModuleLog.line("(IE|Post) ❌ Context not found in click handler");
                return;
            }

            Object media = findMediaViaMenuCreator(thisObj);
            if (media == null) media = findMedia(thisObj);
            if (media == null) {
                ModuleLog.line("(IE|Post) ❌ Media not found in click handler");
                Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_no_media_for_post), Toast.LENGTH_SHORT).show();
                return;
            }

            triggerDownload(ctx, media, thisObj);
        } catch (Throwable t) {
            ModuleLog.line("(IE|Post) ❌ onOptionClicked: " + t);
        }
    }

    private static void triggerDownload(Context ctx, Object media, Object clickHandler) {
        String username = FeedVideoDownloadHook.extractUsernameFromMediaObject(media);
        if (username == null) username = "post";

        String mediaId = "0";
        try {
            Object id = media.getClass().getMethod("getId").invoke(media);
            if (id instanceof String s && !s.isEmpty()) mediaId = s;
        } catch (Throwable ignored) {}

        List<String> urls = FeedVideoDownloadHook.extractAllUrlsFromMedia(ctx, media);

        int viewIdx = urls.size() > 1
                ? ReelDownloadHook.findCarouselIndexFromView(ctx, urls.size())
                : -1;
        final int carouselIdx = viewIdx >= 0 ? viewIdx : findCarouselIndex(clickHandler, urls.size());

        final String finalUser = username;
        final String finalId   = mediaId;
        FeedVideoDownloadHook.mainHandler.post(() ->
                FeedVideoDownloadHook.showPostDownloadDialog(ctx, urls, finalUser, finalId, carouselIdx));
    }

    static int findCarouselIndex(Object obj, int urlCount) {
        if (obj == null || urlCount <= 1) return 0;

        try {
            Field f = obj.getClass().getDeclaredField("A00");
            f.setAccessible(true);
            int v = f.getInt(obj);
            if (v >= 0 && v < urlCount) return v;
        } catch (Throwable ignored) {}

        for (Field f : obj.getClass().getDeclaredFields()) {
            if (f.getType() != int.class) continue;
            f.setAccessible(true);
            try {
                int v = f.getInt(obj);
                if (v > 0 && v < urlCount) return v;
            } catch (Throwable ignored) {}
        }

        return 0;
    }

    private static Context findContext(Object obj) {
        if (obj == null) return null;
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (!Context.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                try {
                    Object v = f.get(obj);
                    if (v instanceof Context c) return c;
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private static Object findMediaViaMenuCreator(Object clickHandler) {
        if (clickHandler == null || menuCreatorClass == null) return null;
        try {
            Object creator = null;
            Class<?> cls = clickHandler.getClass();
            outer:
            while (cls != null && cls != Object.class) {
                for (Field f : cls.getDeclaredFields()) {
                    if (f.getType() != menuCreatorClass) continue;
                    f.setAccessible(true);
                    Object v = f.get(clickHandler);
                    if (v != null) { creator = v; break outer; }
                }
                cls = cls.getSuperclass();
            }
            if (creator == null) return null;

            Class<?> cCls = creator.getClass();
            while (cCls != null && cCls != Object.class) {
                for (Field f : cCls.getDeclaredFields()) {
                    if (f.getType().getName().equals("com.instagram.feed.media.Media")
                            || (mediaClass != null && mediaClass.isAssignableFrom(f.getType()))) {
                        f.setAccessible(true);
                        return f.get(creator);
                    }
                }
                cCls = cCls.getSuperclass();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object findMedia(Object obj) {
        return findMediaDepth(obj, 0);
    }

    private static Object findMediaDepth(Object obj, int depth) {
        if (obj == null || depth > 2) return null;
        Class<?> cls = obj.getClass();
        if (cls.isPrimitive() || cls.getName().startsWith("java.") || cls.getName().startsWith("android."))
            return null;

        List<Object> nextLevel = depth < 2 ? new ArrayList<>() : null;

        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType().isPrimitive()) continue;
                f.setAccessible(true);
                try {
                    Object v = f.get(obj);
                    if (v == null) continue;
                    if (mediaClass != null && mediaClass.isInstance(v)) return v;
                    String name = v.getClass().getName();
                    if (name.equals("com.instagram.feed.media.Media") || name.contains("LiveTreeMediaDict")) return v;
                    if (nextLevel != null && !name.startsWith("java.") && !name.startsWith("android."))
                        nextLevel.add(v);
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }

        if (nextLevel != null) {
            for (Object child : nextLevel) {
                Object found = findMediaDepth(child, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }
}
