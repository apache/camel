package org.apache.camel.component.file.azure;

/**
 * The path separator is {@code /}.
 * <p>
 * The absolute paths start with the path separator, they do not include the share name and they are relative to the
 * share root rather than to the endpoint starting directory.
 */
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

    public static boolean isRoot(String path) {
        // no ambiguity such as / or \
        return path != null && path.equals(SHARE_ROOT);
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

    public static String extractParentPath(String path) {
        if (path == null) {
            return null;
        }

        var lastSeparator = path.lastIndexOf(PATH_SEPARATOR);
        if (lastSeparator == 0) {
            return SHARE_ROOT;
        } else if (lastSeparator > 0) {
            return path.substring(0, lastSeparator);
        }
        return null;
    }

    public static String[] split(String path) {
        return splitToSteps(path, false);
    }

    public static String[] splitToSteps(String path, boolean preserveRootAsStep) {
        if (path == null) {
            return null;
        }

        var includeRoot = preserveRootAsStep && path.startsWith(SHARE_ROOT);
        if (!(includeRoot)) {
            path = ensureRelative(path);
        }

        // no ambiguity such as "/|\\\\"
        var pathSteps = path.split("" + PATH_SEPARATOR);

        if (includeRoot) {
            pathSteps[0] = SHARE_ROOT; // replace leading ""
        }
        return pathSteps;
    }

}
