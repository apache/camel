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
package org.apache.camel.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File utilities.
 */
public final class FileUtil {
    
    public static final int BUFFER_SIZE = 128 * 1024;

    private static final Logger LOG = LoggerFactory.getLogger(FileUtil.class);
    private static final int RETRY_SLEEP_MILLIS = 10;
    /**
     * The System property key for the user directory.
     */
    private static final String USER_DIR_KEY = "user.dir";
    private static final File USER_DIR = new File(System.getProperty(USER_DIR_KEY));
    private static boolean windowsOs = initWindowsOs();

    private FileUtil() {
        // Utils method
    }

    private static boolean initWindowsOs() {
        // initialize once as System.getProperty is not fast
        String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        return osName.contains("windows");
    }

    public static File getUserDir() {
        return USER_DIR;
    }

    /**
     * Normalizes the path to cater for Windows and other platforms
     */
    public static String normalizePath(String path) {
        if (path == null) {
            return null;
        }

        if (isWindows()) {
            // special handling for Windows where we need to convert / to \\
            return path.replace('/', '\\');
        } else {
            // for other systems make sure we use / as separators
            return path.replace('\\', '/');
        }
    }

    /**
     * Returns true, if the OS is windows
     */
    public static boolean isWindows() {
        return windowsOs;
    }

    public static File createTempFile(String prefix, String suffix, File parentDir) throws IOException {
        Objects.requireNonNull(parentDir);

        if (suffix == null) {
            suffix = ".tmp";
        }
        if (prefix == null) {
            prefix = "camel";
        } else if (prefix.length() < 3) {
            prefix = prefix + "camel";
        }

        // create parent folder
        parentDir.mkdirs();

        return Files.createTempFile(parentDir.toPath(), prefix, suffix).toFile();
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
     * Does the name start with a leading separator
     */
    public static boolean hasLeadingSeparator(String name) {
        if (name == null) {
            return false;
        }
        if (name.startsWith("/") || name.startsWith(File.separator)) {
            return true;
        }
        return false;
    }

    /**
     * Strip first leading separator
     */
    public static String stripFirstLeadingSeparator(String name) {
        if (name == null) {
            return null;
        }
        if (name.startsWith("/") || name.startsWith(File.separator)) {
            name = name.substring(1);
        }
        return name;
    }

    /**
     * Strip any trailing separators
     */
    public static String stripTrailingSeparator(String name) {
        if (ObjectHelper.isEmpty(name)) {
            return name;
        }
        
        String s = name;
        
        // there must be some leading text, as we should only remove trailing separators 
        while (s.endsWith("/") || s.endsWith(File.separator)) {
            s = s.substring(0, s.length() - 1);
        }
        
        // if the string is empty, that means there was only trailing slashes, and no leading text
        // and so we should then return the original name as is
        if (ObjectHelper.isEmpty(s)) {
            return name;
        } else {
            // return without trailing slashes
            return s;
        }
    }

    /**
     * Strips any leading paths
     */
    public static String stripPath(String name) {
        if (name == null) {
            return null;
        }
        int posUnix = name.lastIndexOf('/');
        int posWin = name.lastIndexOf('\\');
        int pos = Math.max(posUnix, posWin);

        if (pos != -1) {
            return name.substring(pos + 1);
        }
        return name;
    }

    public static String stripExt(String name) {
        return stripExt(name, false);
    }

    public static String stripExt(String name, boolean singleMode) {
        if (name == null) {
            return null;
        }

        // the name may have a leading path
        int posUnix = name.lastIndexOf('/');
        int posWin = name.lastIndexOf('\\');
        int pos = Math.max(posUnix, posWin);

        if (pos > 0) {
            String onlyName = name.substring(pos + 1);
            int pos2 = singleMode ? onlyName.lastIndexOf('.') : onlyName.indexOf('.');
            if (pos2 > 0) {
                return name.substring(0, pos + pos2 + 1);
            }
        } else {
            // if single ext mode, then only return last extension
            int pos2 = singleMode ? name.lastIndexOf('.') : name.indexOf('.');
            if (pos2 > 0) {
                return name.substring(0, pos2);
            }
        }

        return name;
    }

    public static String onlyExt(String name) {
        return onlyExt(name, false);
    }

    public static String onlyExt(String name, boolean singleMode) {
        if (name == null) {
            return null;
        }
        name = stripPath(name);

        // extension is the first dot, as a file may have double extension such as .tar.gz
        // if single ext mode, then only return last extension
        int pos = singleMode ? name.lastIndexOf('.') : name.indexOf('.');
        if (pos != -1) {
            return name.substring(pos + 1);
        }
        return null;
    }

    /**
     * Returns only the leading path (returns <tt>null</tt> if no path)
     */
    public static String onlyPath(String name) {
        if (name == null) {
            return null;
        }

        int posUnix = name.lastIndexOf('/');
        int posWin = name.lastIndexOf('\\');
        int pos = Math.max(posUnix, posWin);

        if (pos > 0) {
            return name.substring(0, pos);
        } else if (pos == 0) {
            // name is in the root path, so extract the path as the first char
            return name.substring(0, 1);
        }
        // no path in name
        return null;
    }

    /**
     * Compacts a path by stacking it and reducing <tt>..</tt>,
     * and uses OS specific file separators (eg {@link java.io.File#separator}).
     */
    public static String compactPath(String path) {
        return compactPath(path, "" + File.separatorChar);
    }

    /**
     * Compacts a path by stacking it and reducing <tt>..</tt>,
     * and uses the given separator.
     *
     */
    public static String compactPath(String path, char separator) {
        return compactPath(path, "" + separator);
    }

    /**
     * Compacts a file path by stacking it and reducing <tt>..</tt>,
     * and uses the given separator.
     */
    public static String compactPath(String path, String separator) {
        if (path == null) {
            return null;
        }

        if (path.startsWith("http:")) {
            return path;
        }
        
        // only normalize if contains a path separator
        if (path.indexOf('/') == -1 && path.indexOf('\\') == -1)  {
            return path;
        }

        // need to normalize path before compacting
        path = normalizePath(path);

        // preserve ending slash if given in input path
        boolean endsWithSlash = path.endsWith("/") || path.endsWith("\\");

        // preserve starting slash if given in input path
        int cntSlashsAtStart = 0;
        if (path.startsWith("/") || path.startsWith("\\")) {
            cntSlashsAtStart++;
            // for Windows, preserve up to 2 starting slashes, which is necessary for UNC paths.
            if (isWindows() && path.length() > 1 && (path.charAt(1) == '/' || path.charAt(1) == '\\')) {
                cntSlashsAtStart++;
            }
        }
        
        Deque<String> stack = new ArrayDeque<>();

        // separator can either be windows or unix style
        String separatorRegex = "\\\\|/";
        String[] parts = path.split(separatorRegex);
        for (String part : parts) {
            if (part.equals("..") && !stack.isEmpty() && !"..".equals(stack.peek())) {
                // only pop if there is a previous path, which is not a ".." path either
                stack.pop();
            } else if (part.equals(".") || part.isEmpty()) {
                // do nothing because we don't want a path like foo/./bar or foo//bar
            } else {
                stack.push(part);
            }
        }

        // build path based on stack
        StringBuilder sb = new StringBuilder();
        
        for (int i = 0; i < cntSlashsAtStart; i++) {
            sb.append(separator);
        }

        // now we build back using FIFO so need to use descending
        for (Iterator<String> it = stack.descendingIterator(); it.hasNext();) {
            sb.append(it.next());
            if (it.hasNext()) {
                sb.append(separator);
            }
        }

        if (endsWithSlash && stack.size() > 0) {
            sb.append(separator);
        }

        return sb.toString();
    }

    public static void removeDir(File d) {
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

    /**
     * Renames a file.
     *
     * @param from the from file
     * @param to   the to file
     * @param copyAndDeleteOnRenameFail whether to fallback and do copy and delete, if renameTo fails
     * @return <tt>true</tt> if the file was renamed, otherwise <tt>false</tt>
     * @throws java.io.IOException is thrown if error renaming file
     */
    public static boolean renameFile(File from, File to, boolean copyAndDeleteOnRenameFail) throws IOException {
        // do not try to rename non existing files
        if (!from.exists()) {
            return false;
        }

        // some OS such as Windows can have problem doing rename IO operations so we may need to
        // retry a couple of times to let it work
        boolean renamed = false;
        int count = 0;
        while (!renamed && count < 3) {
            if (LOG.isDebugEnabled() && count > 0) {
                LOG.debug("Retrying attempt {} to rename file from: {} to: {}", count, from, to);
            }

            renamed = from.renameTo(to);
            if (!renamed && count > 0) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
            count++;
        }

        // we could not rename using renameTo, so lets fallback and do a copy/delete approach.
        // for example if you move files between different file systems (linux -> windows etc.)
        if (!renamed && copyAndDeleteOnRenameFail) {
            // now do a copy and delete as all rename attempts failed
            LOG.debug("Cannot rename file from: {} to: {}, will now use a copy/delete approach instead", from, to);
            renamed = renameFileUsingCopy(from, to);
        }

        if (LOG.isDebugEnabled() && count > 0) {
            LOG.debug("Tried {} to rename file: {} to: {} with result: {}", count, from, to, renamed);
        }
        return renamed;
    }

    /**
     * Rename file using copy and delete strategy. This is primarily used in
     * environments where the regular rename operation is unreliable.
     * 
     * @param from the file to be renamed
     * @param to the new target file
     * @return <tt>true</tt> if the file was renamed successfully, otherwise <tt>false</tt>
     * @throws IOException If an I/O error occurs during copy or delete operations.
     */
    public static boolean renameFileUsingCopy(File from, File to) throws IOException {
        // do not try to rename non existing files
        if (!from.exists()) {
            return false;
        }

        LOG.debug("Rename file '{}' to '{}' using copy/delete strategy.", from, to);

        copyFile(from, to);
        if (!deleteFile(from)) {
            throw new IOException("Renaming file from '" + from + "' to '" + to + "' failed: Cannot delete file '" + from + "' after copy succeeded");
        }

        return true;
    }

    /**
     * Copies the file
     *
     * @param from  the source file
     * @param to    the destination file
     * @throws IOException If an I/O error occurs during copy operation
     */
    public static void copyFile(File from, File to) throws IOException {
        Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Deletes the file.
     * <p/>
     * This implementation will attempt to delete the file up till three times with one second delay, which
     * can mitigate problems on deleting files on some platforms such as Windows.
     *
     * @param file  the file to delete
     */
    public static boolean deleteFile(File file) {
        // do not try to delete non existing files
        if (!file.exists()) {
            return false;
        }

        // some OS such as Windows can have problem doing delete IO operations so we may need to
        // retry a couple of times to let it work
        boolean deleted = false;
        int count = 0;
        while (!deleted && count < 3) {
            LOG.debug("Retrying attempt {} to delete file: {}", count, file);

            deleted = file.delete();
            if (!deleted && count > 0) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
            count++;
        }


        if (LOG.isDebugEnabled() && count > 0) {
            LOG.debug("Tried {} to delete file: {} with result: {}", count, file, deleted);
        }
        return deleted;
    }

    /**
     * Is the given file an absolute file.
     * <p/>
     * Will also work around issue on Windows to consider files on Windows starting with a \
     * as absolute files. This makes the logic consistent across all OS platforms.
     *
     * @param file  the file
     * @return <tt>true</ff> if its an absolute path, <tt>false</tt> otherwise.
     */
    public static boolean isAbsolute(File file) {
        if (isWindows()) {
            // special for windows
            String path = file.getPath();
            if (path.startsWith(File.separator)) {
                return true;
            }
        }
        return file.isAbsolute();
    }

    /**
     * Creates a new file.
     *
     * @param file the file
     * @return <tt>true</tt> if created a new file, <tt>false</tt> otherwise
     * @throws IOException is thrown if error creating the new file
     */
    public static boolean createNewFile(File file) throws IOException {
        // need to check first
        if (file.exists()) {
            return false;
        }
        try {
            return file.createNewFile();
        } catch (IOException e) {
            // and check again if the file was created as createNewFile may create the file
            // but throw a permission error afterwards when using some NAS
            if (file.exists()) {
                return true;
            } else {
                throw e;
            }
        }
    }

}
