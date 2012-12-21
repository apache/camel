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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.util.Iterator;
import java.util.Locale;
import java.util.Random;
import java.util.Stack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File utilities
 */
public final class FileUtil {
    
    public static final int BUFFER_SIZE = 128 * 1024;

    private static final transient Logger LOG = LoggerFactory.getLogger(FileUtil.class);
    private static final int RETRY_SLEEP_MILLIS = 10;
    private static File defaultTempDir;
    
    // current value of the "user.dir" property
    private static String gUserDir;
    // cached URI object for the current value of the escaped "user.dir" property stored as a URI
    private static URI gUserDirURI;
    // which ASCII characters need to be escaped
    private static boolean gNeedEscaping[] = new boolean[128];
    // the first hex character if a character needs to be escaped
    private static char gAfterEscaping1[] = new char[128];
    // the second hex character if a character needs to be escaped
    private static char gAfterEscaping2[] = new char[128];
    private static char[] gHexChs = {'0', '1', '2', '3', '4', '5', '6', '7',
                                     '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
    // initialize the above 3 arrays
    static {
        for (int i = 0; i <= 0x1f; i++) {
            gNeedEscaping[i] = true;
            gAfterEscaping1[i] = gHexChs[i >> 4];
            gAfterEscaping2[i] = gHexChs[i & 0xf];
        }
        gNeedEscaping[0x7f] = true;
        gAfterEscaping1[0x7f] = '7';
        gAfterEscaping2[0x7f] = 'F';
        char[] escChs = {' ', '<', '>', '#', '%', '"', '{', '}',
                         '|', '\\', '^', '~', '[', ']', '`'};
        int len = escChs.length;
        char ch;
        for (int i = 0; i < len; i++) {
            ch = escChs[i];
            gNeedEscaping[ch] = true;
            gAfterEscaping1[ch] = gHexChs[ch >> 4];
            gAfterEscaping2[ch] = gHexChs[ch & 0xf];
        }
    }
    
    private FileUtil() {
        // Utils method
    }
    

    // To escape the "user.dir" system property, by using %HH to represent
    // special ASCII characters: 0x00~0x1F, 0x7F, ' ', '<', '>', '#', '%'
    // and '"'. It's a static method, so needs to be synchronized.
    // this method looks heavy, but since the system property isn't expected
    // to change often, so in most cases, we only need to return the URI
    // that was escaped before.
    // According to the URI spec, non-ASCII characters (whose value >= 128)
    // need to be escaped too.
    // REVISIT: don't know how to escape non-ASCII characters, especially
    // which encoding to use. Leave them for now.
    public static synchronized URI getUserDir() throws URISyntaxException {
        // get the user.dir property
        String userDir = "";
        try {
            userDir = System.getProperty("user.dir");
        } catch (SecurityException se) {
        }

        // return empty string if property value is empty string.
        if (userDir.length() == 0) {
            return new URI("file", "", "", null, null);
        }
        // compute the new escaped value if the new property value doesn't
        // match the previous one
        if (gUserDirURI != null && userDir.equals(gUserDir)) {
            return gUserDirURI;
        }

        // record the new value as the global property value
        gUserDir = userDir;

        char separator = java.io.File.separatorChar;
        userDir = userDir.replace(separator, '/');

        int len = userDir.length(); 
        int ch;
        StringBuffer buffer = new StringBuffer(len * 3);
        // change C:/blah to /C:/blah
        if (len >= 2 && userDir.charAt(1) == ':') {
            ch = Character.toUpperCase(userDir.charAt(0));
            if (ch >= 'A' && ch <= 'Z') {
                buffer.append('/');
            }
        }

        // for each character in the path
        int i = 0;
        for (; i < len; i++) {
            ch = userDir.charAt(i);
            // if it's not an ASCII character, break here, and use UTF-8 encoding
            if (ch >= 128) {
                break;
            }
            if (gNeedEscaping[ch]) {
                buffer.append('%');
                buffer.append(gAfterEscaping1[ch]);
                buffer.append(gAfterEscaping2[ch]);
                // record the fact that it's escaped
            } else {
                buffer.append((char)ch);
            }
        }

        // we saw some non-ascii character
        if (i < len) {
            // get UTF-8 bytes for the remaining sub-string
            byte[] bytes = null;
            byte b;
            try {
                bytes = userDir.substring(i).getBytes("UTF-8");
            } catch (java.io.UnsupportedEncodingException e) {
                // should never happen
                return new URI("file", "", userDir, null, null);
            }
            len = bytes.length;

            // for each byte
            for (i = 0; i < len; i++) {
                b = bytes[i];
                // for non-ascii character: make it positive, then escape
                if (b < 0) {
                    ch = b + 256;
                    buffer.append('%');
                    buffer.append(gHexChs[ch >> 4]);
                    buffer.append(gHexChs[ch & 0xf]);
                } else if (gNeedEscaping[b]) {
                    buffer.append('%');
                    buffer.append(gAfterEscaping1[b]);
                    buffer.append(gAfterEscaping2[b]);
                } else {
                    buffer.append((char)b);
                }
            }
        }

        // change blah/blah to blah/blah/
        if (!userDir.endsWith("/")) {
            buffer.append('/');
        }

        gUserDirURI = new URI("file", "", buffer.toString(), null, null);

        return gUserDirURI;
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
        return compactPath(path, File.separatorChar);
    }

    /**
     * Compacts a path by stacking it and reducing <tt>..</tt>,
     * and uses the given separator.
     */
    public static String compactPath(String path, char separator) {
        if (path == null) {
            return null;
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
        boolean startsWithSlash = path.startsWith("/") || path.startsWith("\\");
        
        Stack<String> stack = new Stack<String>();

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
        
        if (startsWithSlash) {
            sb.append(separator);
        }
        
        for (Iterator<String> it = stack.iterator(); it.hasNext();) {
            sb.append(it.next());
            if (it.hasNext()) {
                sb.append(separator);
            }
        }

        if (endsWithSlash) {
            sb.append(separator);
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
                LOG.debug("Retrying attempt {} to rename file from: {} to: {}", new Object[]{count, from, to});
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
            copyFile(from, to);
            if (!deleteFile(from)) {
                throw new IOException("Renaming file from: " + from + " to: " + to + " failed due cannot delete from file: " + from + " after copy succeeded");
            } else {
                renamed = true;
            }
        }

        if (LOG.isDebugEnabled() && count > 0) {
            LOG.debug("Tried {} to rename file: {} to: {} with result: {}", new Object[]{count, from, to, renamed});
        }
        return renamed;
    }

    public static void copyFile(File from, File to) throws IOException {
        FileChannel in = new FileInputStream(from).getChannel();
        FileChannel out = new FileOutputStream(to).getChannel();
        try {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Using FileChannel to copy from: " + in + " to: " + out);
            }

            long size = in.size();
            long position = 0;
            while (position < size) {
                position += in.transferTo(position, BUFFER_SIZE, out);
            }
        } finally {
            IOHelper.close(in, from.getName(), LOG);
            IOHelper.close(out, to.getName(), LOG);
        }
    }

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
            LOG.debug("Tried {} to delete file: {} with result: {}", new Object[]{count, file, deleted});
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

}
