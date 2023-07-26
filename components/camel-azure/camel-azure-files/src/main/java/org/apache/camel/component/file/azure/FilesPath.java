/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.file.azure;

/**
 * The path separator is {@code /}.
 * <p>
 * The absolute paths start with the path separator, they do not include the share name and they are relative to the
 * share root rather than to the endpoint starting directory.
 */
public final class FilesPath {

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

    public static boolean isEmptyStep(String path) {
        // TODO blank step like " " could be valid, but Windows trims trailing spaces
        return isEmpty(path) || path.equals(CWD);
    }

    public static boolean isRoot(String path) {
        // no ambiguity such as / or \
        return path != null && path.equals(SHARE_ROOT);
    }

    public static boolean isAbsolute(String path) {
        // no ambiguity such as / or \
        return path != null && path.startsWith(SHARE_ROOT);
    }

    public static String ensureRelative(String path) {
        if (path.startsWith(SHARE_ROOT)) {
            return path.substring(1);
        }
        return path;
    }

    public static String concat(String dir, String subPath) {
        if (isEmptyStep(dir)) {
            return subPath;
        }
        if (isEmptyStep(subPath)) {
            return dir;
        }
        return hasTrailingSeparator(dir) ? dir + subPath : dir + PATH_SEPARATOR + subPath;
    }

    public static String trimTrailingSeparator(String path) {
        if (path == null) {
            return null;
        }
        if (hasTrailingSeparator(path)) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }

    public static boolean hasTrailingSeparator(String path) {
        return path.charAt(path.length() - 1) == PATH_SEPARATOR;
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

        if (preserveRootAsStep && path.equals(SHARE_ROOT)) {
            return new String[] { SHARE_ROOT };
        }

        var includeRoot = preserveRootAsStep && path.startsWith(SHARE_ROOT);
        if (!includeRoot) {
            path = ensureRelative(path);
        }

        // no ambiguity such as "/|\\\\"
        var pathSteps = path.split("" + PATH_SEPARATOR);

        if (includeRoot) {
            pathSteps[0] = SHARE_ROOT; // replace leading ""
        }
        return pathSteps;
    }

    public static String trimLeadingSeparator(String path) {
        if (path == null) {
            return null;
        }
        if (isAbsolute(path)) {
            return path.substring(1);
        }
        return path;
    }

}
