package ps.reso.instaeclipse.utils.users;

import java.lang.reflect.Method;

public class UserUtils {

    public static Method userUsernameGetter;

    public static String callUsernameGetter(Object user) {
        if (user == null) return null;

        if (userUsernameGetter != null) {
            try {
                Object r = userUsernameGetter.invoke(user);
                if (r instanceof String s && isValidUsername(s)) {
                    return s;
                }
            } catch (Throwable ignored) {}
        }

        for (Method m : user.getClass().getDeclaredMethods()) {
            if (m.getParameterCount() != 0 || !m.getReturnType().equals(String.class)) continue;
            try {
                m.setAccessible(true);
                Object r = m.invoke(user);
                if (r instanceof String s && isValidUsername(s)) {
                    userUsernameGetter = m;
                    return s;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public static boolean isValidUsername(String s) {
        if (s == null || s.isEmpty()) return false;

        return s.matches("^[a-z0-9._]{2,30}$");
    }
}
