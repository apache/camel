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
package org.apache.camel.component.file.remote;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

import org.apache.camel.Component;
import org.apache.camel.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Various FTP utils.
 */
public final class FtpUtils {

    private static final Logger LOG = LoggerFactory.getLogger(FtpUtils.class);

    private FtpUtils() {
    }
    
    public static String extractDirNameFromAbsolutePath(String path) {
        // default is unix so try with '/'
        // otherwise force File.separator
        if (path.endsWith("/") || path.endsWith("\\")) {
            path = path.substring(0, path.length() - 1);
        }
        return FileUtil.stripPath(path);
    }

    /**
     * Compacts a path by stacking it and reducing <tt>..</tt>,
     * and uses OS specific file separators (eg {@link java.io.File#separator}).
     * <p/>
     * <b>Important: </b> This implementation works for the camel-ftp component
     * for various FTP clients and FTP servers using different platforms and whatnot.
     * This implementation has been working for many Camel releases, and is included here
     * to restore patch compatibility with the Camel releases.
     */
    public static String compactPath(String path) {
        if (path == null) {
            return null;
        }

        // only normalize if contains a path separator
        if (path.indexOf(File.separator) == -1) {
            return path;
        }

        // preserve ending slash if given in input path
        boolean endsWithSlash = path.endsWith("/") || path.endsWith("\\");

        // preserve starting slash if given in input path
        boolean startsWithSlash = path.startsWith("/") || path.startsWith("\\");

        Deque<String> stack = new ArrayDeque<>();

        String separatorRegex = File.separator;
        if (FileUtil.isWindows()) {
            separatorRegex = "\\\\";
        }
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
            sb.append(File.separator);
        }

        // now we build back using FIFO so need to use descending
        for (Iterator<String> it = stack.descendingIterator(); it.hasNext();) {
            sb.append(it.next());
            if (it.hasNext()) {
                sb.append(File.separator);
            }
        }

        if (endsWithSlash && stack.size() > 0) {
            sb.append(File.separator);
        }

        // there has been problems with double slashes,
        // so avoid this by removing any 2nd slash
        if (sb.length() >= 2) {
            boolean firstSlash = sb.charAt(0) == '/' || sb.charAt(0) == '\\';
            boolean secondSlash = sb.charAt(1) == '/' || sb.charAt(1) == '\\';
            if (firstSlash && secondSlash) {
                // remove 2nd clash
                sb = sb.replace(1, 2, "");
            }
        }

        return sb.toString();
    }

    /**
     * Checks whether directory used in ftp/ftps/sftp endpoint URI is relative.
     * Absolute path will be converted to relative path and a WARN will be printed.
     * @see <a href="http://camel.apache.org/ftp2.html">FTP/SFTP/FTPS Component</a>
     * @param ftpComponent
     * @param configuration
     */
    public static void ensureRelativeFtpDirectory(Component ftpComponent, RemoteFileConfiguration configuration) {
        if (FileUtil.hasLeadingSeparator(configuration.getDirectoryName())) {
            String relativePath = FileUtil.stripLeadingSeparator(configuration.getDirectoryName());
            LOG.warn(String.format("%s doesn't support absolute paths, \"%s\" will be converted to \"%s\". "
                    + "After Camel 2.16, absolute paths will be invalid.",
                    ftpComponent.getClass().getSimpleName(),
                    configuration.getDirectoryName(),
                    relativePath));
            configuration.setDirectory(relativePath);
            configuration.setDirectoryName(relativePath);
        }
    }

}
