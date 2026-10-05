package ps.reso.instaeclipse.utils.core;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ps.reso.instaeclipse.utils.log.ModuleLog;

public final class IgDex {

    private static final String POOL_CACHE_KEY = "IgDex_StringPoolClasses";
    private static final int POOL_MISS_LIMIT = 64;
    private static final int POOL_MAX_ENTRIES = 200_000;
    private static volatile Set<String> poolClasses;
    private static volatile Map<String, List<PoolRef>> poolIndex;

    private IgDex() {}

    private static final class PoolRef {
        final String descriptor;
        final int index;

        PoolRef(String descriptor, int index) {
            this.descriptor = descriptor;
            this.index = index;
        }
    }

    public static List<MethodData> findMethod(DexKitBridge bridge, FindMethod query) {
        List<MethodData> raw = bridge.findMethod(query);
        List<MethodData> out = new ArrayList<>(raw.size());
        for (MethodData md : raw) {
            if (!isPoolClass(bridge, md.getClassName())) out.add(md);
        }
        return out;
    }

    public static List<ClassData> findClass(DexKitBridge bridge, FindClass query) {
        List<ClassData> raw = bridge.findClass(query);
        List<ClassData> out = new ArrayList<>(raw.size());
        for (ClassData cd : raw) {
            if (!isPoolClass(bridge, cd.getName())) out.add(cd);
        }
        return out;
    }

    public static boolean isPoolClass(DexKitBridge bridge, String className) {
        return className != null && className.startsWith("X.") && poolClasses(bridge).contains(className);
    }

    private static Set<String> poolClasses(DexKitBridge bridge) {
        Set<String> pools = poolClasses;
        if (pools != null) return pools;
        synchronized (IgDex.class) {
            if (poolClasses != null) return poolClasses;
            pools = new HashSet<>();
            String cached = DexKitCache.isCacheValid() ? DexKitCache.loadString(POOL_CACHE_KEY) : null;
            if (cached != null) {
                for (String name : cached.split(";")) {
                    if (!name.isEmpty()) pools.add(name);
                }
            } else if (bridge != null) {
                try {
                    List<MethodData> candidates = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                            .modifiers(Modifier.STATIC)
                            .returnType("java.lang.String")
                            .paramTypes("int")));
                    for (MethodData md : candidates) {
                        String name = md.getClassName();
                        if (name.startsWith("X.") && !pools.contains(name) && isPoolStructure(bridge, name)) {
                            pools.add(name);
                        }
                    }
                    DexKitCache.saveString(POOL_CACHE_KEY, String.join(";", pools));
                } catch (Throwable t) {
                    ModuleLog.line("(IgDex) pool discovery failed: " + t.getMessage());
                }
            }
            poolClasses = pools;
            return pools;
        }
    }

    private static boolean isPoolStructure(DexKitBridge bridge, String className) {
        try {
            ClassData cd = bridge.getClassData(className);
            if (cd == null || !cd.getFields().isEmpty()) return false;
            MethodData only = null;
            int real = 0;
            for (MethodData m : cd.getMethods()) {
                if (m.isMethod()) {
                    real++;
                    only = m;
                }
            }
            return real == 1 && isPoolSignature(only);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isPoolSignature(MethodData m) {
        return m != null && Modifier.isStatic(m.getModifiers())
                && "java.lang.String".equals(m.getReturnTypeName())
                && m.getParamTypeNames().size() == 1 && "int".equals(m.getParamTypeNames().get(0));
    }

    public static List<MethodData> findMethodsUsingAll(DexKitBridge bridge, ClassLoader classLoader, String... strings) {
        if (bridge == null || strings == null || strings.length == 0) return Collections.emptyList();
        Map<String, MethodData> result = null;
        for (String s : strings) {
            Map<String, MethodData> users = methodsUsing(bridge, classLoader, s);
            if (result == null) {
                result = users;
            } else {
                result.keySet().retainAll(users.keySet());
            }
            if (result.isEmpty()) break;
        }
        return result == null ? Collections.emptyList() : new ArrayList<>(result.values());
    }

    private static Map<String, MethodData> methodsUsing(DexKitBridge bridge, ClassLoader classLoader, String s) {
        Map<String, MethodData> users = new LinkedHashMap<>();
        try {
            for (MethodData md : findMethod(bridge, FindMethod.create().matcher(MethodMatcher.create().usingStrings(s)))) {
                users.put(md.getDescriptor(), md);
            }
        } catch (Throwable ignored) {}
        for (PoolRef ref : poolRefs(bridge, classLoader, s)) {
            try {
                List<MethodData> hits = findMethod(bridge, FindMethod.create().matcher(MethodMatcher.create()
                        .addInvoke(ref.descriptor)
                        .addUsingNumber(ref.index)));
                for (MethodData md : hits) users.put(md.getDescriptor(), md);
            } catch (Throwable ignored) {}
        }
        return users;
    }

    private static List<PoolRef> poolRefs(DexKitBridge bridge, ClassLoader classLoader, String needle) {
        Map<String, List<PoolRef>> index = buildPoolIndex(bridge, classLoader);
        List<PoolRef> refs = new ArrayList<>();
        for (Map.Entry<String, List<PoolRef>> e : index.entrySet()) {
            if (e.getKey().contains(needle)) refs.addAll(e.getValue());
        }
        return refs;
    }

    private static Map<String, List<PoolRef>> buildPoolIndex(DexKitBridge bridge, ClassLoader classLoader) {
        Map<String, List<PoolRef>> index = poolIndex;
        if (index != null) return index;
        synchronized (IgDex.class) {
            if (poolIndex != null) return poolIndex;
            index = new HashMap<>();
            List<Method> pools = findPoolMethods(bridge, classLoader);
            for (Method pool : pools) {
                String descriptor = descriptorOf(pool);
                Object fallback = invokePool(pool, Integer.MIN_VALUE);
                int misses = 0;
                for (int i = 0; misses < POOL_MISS_LIMIT && i < POOL_MAX_ENTRIES; i++) {
                    Object value = invokePool(pool, i);
                    if (!(value instanceof String) || value.equals(fallback)) {
                        misses++;
                        continue;
                    }
                    misses = 0;
                    List<PoolRef> list = index.get(value);
                    if (list == null) {
                        list = new ArrayList<>(1);
                        index.put((String) value, list);
                    }
                    list.add(new PoolRef(descriptor, i));
                }
            }
            ModuleLog.line("(IgDex) string pools=" + pools.size() + " entries=" + index.size());
            poolIndex = index;
            return index;
        }
    }

    private static Object invokePool(Method pool, int index) {
        try {
            return pool.invoke(null, index);
        } catch (Throwable t) {
            return null;
        }
    }

    private static List<Method> findPoolMethods(DexKitBridge bridge, ClassLoader classLoader) {
        List<Method> pools = new ArrayList<>();
        for (String name : poolClasses(bridge)) {
            try {
                for (Method m : classLoader.loadClass(name).getDeclaredMethods()) {
                    if (Modifier.isStatic(m.getModifiers()) && m.getReturnType() == String.class
                            && m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == int.class) {
                        m.setAccessible(true);
                        pools.add(m);
                    }
                }
            } catch (Throwable ignored) {}
        }
        return pools;
    }

    private static String descriptorOf(Method m) {
        return "L" + m.getDeclaringClass().getName().replace('.', '/') + ";->" + m.getName() + "(I)Ljava/lang/String;";
    }
}
