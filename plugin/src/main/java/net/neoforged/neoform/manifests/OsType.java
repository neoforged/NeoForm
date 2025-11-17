package net.neoforged.neoform.manifests;

import com.google.gson.annotations.SerializedName;

public enum OsType {
    @SerializedName("windows")
    WINDOWS,
    @SerializedName("linux")
    LINUX,
    @SerializedName("osx")
    MAC,
    UNKNOWN;

    private static final OsType CURRENT;

    static {
        var osName = System.getProperty("os.name");
        // The following matches the logic in Apache Commons Lang 3 SystemUtils
        if (osName.startsWith("Linux") || osName.startsWith("LINUX")) {
            CURRENT = OsType.LINUX;
        } else if (osName.startsWith("Mac OS X")) {
            CURRENT = OsType.MAC;
        } else if (osName.startsWith("Windows")) {
            CURRENT = OsType.WINDOWS;
        } else {
            CURRENT = OsType.UNKNOWN;
        }
    }

    public static OsType current() {
        return CURRENT;
    }
}
