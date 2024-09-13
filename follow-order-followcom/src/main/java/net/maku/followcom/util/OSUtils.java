package net.maku.followcom.util;

import org.springframework.util.SystemPropertyUtils;

public class OSUtils {
    public static boolean isWindows() {
        String os = SystemPropertyUtils.resolvePlaceholders("${os.name}").toLowerCase();
        return os.contains("win");
    }
}
