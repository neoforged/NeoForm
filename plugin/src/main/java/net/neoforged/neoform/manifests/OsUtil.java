package net.neoforged.neoform.manifests;

public final class OsUtil {
    private OsUtil() {
    }

    public static boolean isWindows() {
        return OsType.current() == OsType.WINDOWS;
    }

    public static boolean isLinux() {
        return OsType.current() == OsType.LINUX;
    }

    public static boolean isMac() {
        return OsType.current() == OsType.MAC;
    }
}
