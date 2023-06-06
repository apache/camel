package org.apache.camel.component.file.azure;

public class FilesPath {

    public static final String PARENT = "..";

    public static final String CWD = ".";

    public static final String SHARE_ROOT = "/";
    
    public static final char PATH_SEPARATOR = '/';

    private FilesPath() {
        // for now, non-constructible
    }

    public static boolean isEmpty(String path) {
        return path == null || path.isBlank();
    }

    public static String ensureRelative(String path) {
        if (path.startsWith(SHARE_ROOT)) {
            return path.substring(1);
        }
        return path;
    }

    public static String trimParentPath(String path) {
        if (path == null) {
            return null;
        }

        var lastSeparator = path.lastIndexOf(PATH_SEPARATOR);
        if (lastSeparator != -1) {
            return path.substring(lastSeparator + 1);
        }
        return path;
    }
}
