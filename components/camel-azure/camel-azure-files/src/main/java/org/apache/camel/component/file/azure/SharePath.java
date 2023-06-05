package org.apache.camel.component.file.azure;

public class SharePath {

    public static final String PARENT = "..";

    public static final String CWD = ".";

    public static final String SHARE_ROOT = "/";

    private SharePath() {
        // for now, non constructible
    }

    public static boolean isEmpty(String path) {
        return path == null || path.isBlank();
    }
}
