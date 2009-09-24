/**
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
package org.apache.camel.util;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Random;
import java.util.Stack;

/**
 * File utilities
 */
public final class FileUtil {
    
    private static final int RETRY_SLEEP_MILLIS = 10;
    private static File defaultTempDir;

    private FileUtil() {
    }

    /**
     * Normalizes the path to cater for Windows and other platforms
     */
    public static String normalizePath(String path) {
        // special handling for Windows where we need to convert / to \\
        if (path != null && isWindows() && path.indexOf('/') >= 0) {
            return path.replace('/', '\\');
        }
        return path;
    }
    
    public static boolean isWindows() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.US);
        return osName.indexOf("windows") > -1;
    }

    public static File createTempFile(String prefix, String suffix) throws IOException {
        return createTempFile(prefix, suffix, null);
    }

    public static File createTempFile(String prefix, String suffix, File parentDir) throws IOException {
        File parent = (parentDir == null) ? getDefaultTempDir() : parentDir;
            
        if (suffix == null) {
            suffix = ".tmp";
        }
        if (prefix == null) {
            prefix = "camel";
        } else if (prefix.length() < 3) {
            prefix = prefix + "camel";
        }

        // create parent folder
        parent.mkdirs();

        return File.createTempFile(prefix, suffix, parent);
    }

    /**
     * Strip any leading separators
     */
    public static String stripLeadingSeparator(String name) {
        if (name == null) {
            return null;
        }
        while (name.startsWith("/") || name.startsWith(File.separator)) {
            name = name.substring(1);
        }
        return name;
    }

    /**
     * Strip any trailing separators
     */
    public static String stripTrailingSeparator(String name) {
        if (name == null) {
            return null;
        }
        while (name.endsWith("/") || name.endsWith(File.separator)) {
            name = name.substring(0, name.length() - 1);
        }
        return name;
    }

    /**
     * Strips any leading paths
     */
    public static String stripPath(String name) {
        if (name == null) {
            return null;
        }
        int pos = name.lastIndexOf('/');
        if (pos == -1) {
            pos = name.lastIndexOf(File.separator);
        }
        if (pos != -1) {
            return name.substring(pos + 1);
        }
        return name;
    }

    public static String stripExt(String name) {
        if (name == null) {
            return null;
        }
        int pos = name.lastIndexOf('.');
        if (pos != -1) {
            return name.substring(0, pos);
        }
        return name;
    }

    /**
     * Returns only the leading path (returns <tt>null</tt> if no path)
     */
    public static String onlyPath(String name) {
        if (name == null) {
            return null;
        }
        int pos = name.lastIndexOf('/');
        if (pos == -1) {
            pos = name.lastIndexOf(File.separator);
        }
        if (pos != -1) {
            return name.substring(0, pos);
        }
        // no path
        return null;
    }

    /**
     * Compacts a path by stacking it and reducing <tt>..</tt>
     */
    public static String compactPath(String path) {
        if (path == null) {
            return null;
        }

        // only normalize path if it contains .. as we want to avoid: path/../sub/../sub2 as this can leads to trouble
        if (path.indexOf("..") == -1) {
            return path;
        }

        // only normalize if contains a path separator
        if (path.indexOf(File.separator) == -1) {
            return path;
        }

        Stack<String> stack = new Stack<String>();
        
        String separatorRegex = File.separator;
        if (FileUtil.isWindows()) {
            separatorRegex = "\\\\";
        }
        String[] parts = path.split(separatorRegex);
        for (String part : parts) {
            if (part.equals("..") && !stack.isEmpty()) {
                // only pop if there is a previous path
                stack.pop();
            } else {
                stack.push(part);
            }
        }

        // build path based on stack
        StringBuilder sb = new StringBuilder();
        for (Iterator it = stack.iterator(); it.hasNext();) {
            sb.append(it.next());
            if (it.hasNext()) {
                sb.append(File.separator);
            }
        }

        return sb.toString();
    }

    private static synchronized File getDefaultTempDir() {
        if (defaultTempDir != null && defaultTempDir.exists()) {
            return defaultTempDir;
        }

        String s = System.getProperty("java.io.tmpdir");
        File checkExists = new File(s);
        if (!checkExists.exists()) {
            throw new RuntimeException("The directory "
                                   + checkExists.getAbsolutePath()
                                   + " does not exist, please set java.io.tempdir"
                                   + " to an existing directory");
        }

        // create a sub folder with a random number
        Random ran = new Random();
        int x = ran.nextInt(1000000);

        File f = new File(s, "camel-tmp-" + x);
        while (!f.mkdir()) {
            x = ran.nextInt(1000000);
            f = new File(s, "camel-tmp-" + x);
        }

        defaultTempDir = f;

        // create shutdown hook to remove the temp dir
        Thread hook = new Thread() {
            @Override
            public void run() {
                removeDir(defaultTempDir);
            }
        };
        Runtime.getRuntime().addShutdownHook(hook);

        return defaultTempDir;
    }

    private static void removeDir(File d) {
        String[] list = d.list();
        if (list == null) {
            list = new String[0];
        }
        for (String s : list) {
            File f = new File(d, s);
            if (f.isDirectory()) {
                removeDir(f);
            } else {
                delete(f);
            }
        }
        delete(d);
    }

    private static void delete(File f) {
        if (!f.delete()) {
            if (isWindows()) {
                System.gc();
            }
            try {
                Thread.sleep(RETRY_SLEEP_MILLIS);
            } catch (InterruptedException ex) {
                // Ignore Exception
            }
            if (!f.delete()) {
                f.deleteOnExit();
            }
        }
    }

}
