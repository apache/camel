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
package org.apache.camel.tooling.util;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class FileUtil {

    private static final ConcurrentMap<String, Object> LOCKS = new ConcurrentHashMap<>();

    private FileUtil() {
    }

    /**
     * Update a file with the given string content if neeed. The file won't be modified if the content is already the
     * same.
     *
     * @param  path        the path of the file to update
     * @param  newdata     the new string data, <code>null</code> to delete the file
     * @return             <code>true</code> if the file was modified, <code>false</code> otherwise
     * @throws IOException if an exception occurs
     */
    public static boolean updateFile(Path path, String newdata) throws IOException {
        return updateFile(path, newdata != null ? newdata.getBytes() : null);
    }

    /**
     * Update a file with the given string content if neeed. The file won't be modified if the content is already the
     * same.
     *
     * @param  path        the path of the file to update
     * @param  newdata     the new string data, <code>null</code> to delete the file
     * @param  encoding    the encoding to use
     * @return             <code>true</code> if the file was modified, <code>false</code> otherwise
     * @throws IOException if an exception occurs
     */
    public static boolean updateFile(Path path, String newdata, Charset encoding) throws IOException {
        return updateFile(path, newdata != null ? newdata.getBytes(encoding) : null);
    }

    /**
     * Update a file with the given binary content if neeed. The file won't be modified if the content is already the
     * same.
     *
     * @param  path        the path of the file to update
     * @param  newdata     the new binary data, <code>null</code> to delete the file
     * @return             <code>true</code> if the file was modified, <code>false</code> otherwise
     * @throws IOException if an exception occurs
     */
    public static boolean updateFile(Path path, byte[] newdata) throws IOException {
        Object lock = LOCKS.computeIfAbsent(path.toString(), k -> new Object());
        synchronized (lock) {
            return doUpdateFile(path, newdata);
        }
    }

    private static boolean doUpdateFile(Path path, byte[] newdata) throws IOException {
        if (newdata == null) {
            if (!Files.exists(path)) {
                return false;
            }
            Files.delete(path);
            return true;
        } else {
            final byte[] olddata = readFile(path);
            if (Arrays.equals(olddata, newdata)) {
                return false;
            }
            Files.createDirectories(path.getParent());
            Files.write(path, newdata, StandardOpenOption.WRITE,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        }
    }

    private static byte[] readFile(Path path) throws IOException {
        if (Files.isReadable(path)) {
            return Files.readAllBytes(path);
        }

        return new byte[0];
    }

    /**
     * Read the content of the input file and update the target accordingly
     *
     * @param  from        the source file
     * @param  to          the target file
     * @throws IOException if an exception occurs
     */
    public static void updateFile(Path from, Path to) throws IOException {
        updateFile(to, Files.readAllBytes(from));
    }

}
