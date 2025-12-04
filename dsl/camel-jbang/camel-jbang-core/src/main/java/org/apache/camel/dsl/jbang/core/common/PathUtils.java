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

package org.apache.camel.dsl.jbang.core.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * Utility methods for working with Path and File objects.
 */
public final class PathUtils {

    private PathUtils() {
        // Utility class
    }

    /**
     * Delete a file if it exists.
     *
     * @param  path the path to delete
     * @return      true if the file was deleted, false otherwise
     */
    public static boolean deleteFile(Path path) {
        try {
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Safely write text to a file, ignoring any exceptions.
     *
     * @param  text the text to write
     * @param  path the path to write to
     * @return      true if the write was successful, false otherwise
     */
    public static boolean writeTextSafely(String text, Path path) {
        try {
            Files.writeString(path, text);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Copy content from an InputStream to a Path.
     *
     * @param  input       the input stream to copy from
     * @param  target      the target path to copy to
     * @param  closeInput  whether to close the input stream after copying
     * @throws IOException if an I/O error occurs
     */
    public static void copyFromStream(InputStream input, Path target, boolean closeInput) throws IOException {
        try {
            // Ensure parent directories exist
            Path parent = target.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            // Copy the stream to the target path
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            if (closeInput && input != null) {
                input.close();
            }
        }
    }

    /**
     * Delete a directory and all its contents.
     *
     * @param  directory the directory to delete
     * @return           true if the directory was deleted, false otherwise
     */
    public static boolean deleteDirectory(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return false;
        }

        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    // Ignore
                }
            });
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Copy a directory and all its contents to another directory.
     *
     * @param  sourceDir   the source directory
     * @param  targetDir   the target directory
     * @throws IOException if an I/O error occurs
     */
    public static void copyDirectory(Path sourceDir, Path targetDir) throws IOException {
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            return;
        }

        // Create target directory if it doesn't exist
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }

        // Copy all files and subdirectories
        try (Stream<Path> stream = Files.walk(sourceDir)) {
            stream.forEach(source -> {
                try {
                    Path target = targetDir.resolve(sourceDir.relativize(source));
                    if (Files.isDirectory(source)) {
                        if (!Files.exists(target)) {
                            Files.createDirectories(target);
                        }
                    } else {
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
