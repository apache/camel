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

package org.apache.camel.dsl.jbang.export.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;

/**
 * Helper class provides export utilities for instance when copying files from temporary work folder to export directory
 * target.
 */
public final class ExportHelper {

    /**
     * The settings that the silent run writes in the work directory: the classified source files, the dependencies and
     * other details the export reads.
     */
    public static final String RUN_SETTINGS_FILE = "camel-jbang-run.properties";

    public static final int MAX_RESOURCE_DIR_FILES = 1000;

    private ExportHelper() {
        // prevent instantiation of utility class.
    }

    public static Path validateResourceDir(String dir) {
        Path path = Paths.get(dir).normalize();
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("--resource-dirs only accepts relative paths: " + dir);
        }
        if (path.getFileName() != null && path.getFileName().toString().equals("..")) {
            throw new IllegalArgumentException(
                    "--resource-dirs path must end with a named directory, not '..': " + dir);
        }
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("--resource-dirs path is not a directory: " + dir);
        }
        return path;
    }

    public static List<Path> walkResourceDir(Path dirPath) throws IOException {
        try (Stream<Path> walk = Files.walk(dirPath)) {
            List<Path> files = walk.filter(Files::isRegularFile).toList();
            if (files.size() > MAX_RESOURCE_DIR_FILES) {
                throw new IllegalArgumentException(
                        "--resource-dirs directory contains " + files.size()
                                                   + " files which exceeds the limit of " + MAX_RESOURCE_DIR_FILES + ": "
                                                   + dirPath);
            }
            return files;
        }
    }

    /**
     * Replaces the files with the (not hidden) files of the given directory.
     */
    public static void dirToFiles(String dir, List<String> files) {
        files.clear();
        boolean currentDir = Paths.get(dir).normalize().toString().isEmpty();
        String base = currentDir ? "" : FileUtil.stripTrailingSeparator(dir) + "/";
        try (Stream<Path> paths = Files.list(Paths.get(dir))) {
            paths.filter(p -> {
                try {
                    return Files.isRegularFile(p) && !Files.isHidden(p);
                } catch (IOException e) {
                    return false;
                }
            })
                    .forEach(f -> files.add(base + f.getFileName()));
        } catch (IOException e) {
            // Ignore
        }
    }

    public static void cleanExportDir(String dir) {
        cleanExportDir(dir, true);
    }

    public static void cleanExportDir(String dir, boolean keepHidden) {
        Path targetPath = Paths.get(dir);
        if (!Files.exists(targetPath)) {
            return;
        }

        try (Stream<Path> paths = Files.list(targetPath)) {
            paths.forEach(path -> {
                try {
                    boolean isHidden = Files.isHidden(path);
                    if (Files.isDirectory(path) && (!keepHidden || !isHidden)) {
                        PathUtils.deleteDirectory(path);
                    } else if (Files.isRegularFile(path) && (!keepHidden || !isHidden)) {
                        Files.deleteIfExists(path);
                    }
                } catch (IOException e) {
                    // Ignore
                }
            });
        } catch (IOException e) {
            // Ignore
        }
    }

    public static void safeCopy(
            ClassLoader classLoader, String scheme, Path source, Path target, boolean override)
            throws Exception {
        if ("classpath".equals(scheme)) {
            // in windows the source object contains the windows file separator
            // the getResourceAsStream cannot find the resource file inside the jar in this case
            // then we have to replace the windows file separator to unix style
            try (var ins = classLoader.getResourceAsStream(source.toString().replace("\\", "/"));
                 var outs = Files.newOutputStream(target)) {
                IOHelper.copy(ins, outs);
            }
        } else {
            safeCopy(source, target, override);
        }
    }

    public static void safeCopy(Path source, Path target, boolean override) throws Exception {
        if (!Files.exists(source)) {
            return;
        }

        if (Files.isDirectory(source)) {
            // flatten files if they are from a directory
            try (var stream = Files.list(source)) {
                stream.filter(Files::isRegularFile)
                        .forEach(child -> {
                            try {
                                safeCopy(child, target.resolve(child.getFileName()), override);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            }
            return;
        }

        if (!Files.exists(target)) {
            Files.copy(source, target);
        } else if (override) {
            Files.copy(source, target,
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void safeCopy(InputStream source, Path target) throws Exception {
        if (source == null) {
            return;
        }

        Path dir = target.getParent();
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }

        if (!Files.exists(target)) {
            Files.copy(source, target);
        }
    }

    public static String exportPackageName(String groupId, String artifactId, String packageName) {
        if ("false".equalsIgnoreCase(packageName)) {
            return null; // package names are turned off (we should use root package)
        }
        if (packageName != null) {
            return packageName; // use specific package name
        }

        // compute package name based on Maven GAV
        // for package name it must be in lower-case and alpha/numeric
        String s = groupId + "." + artifactId;
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch == '.' || Character.isAlphabetic(ch) || Character.isDigit(ch)) {
                ch = Character.toLowerCase(ch);
                sb.append(ch);
            }
        }
        return sb.toString();
    }

}
