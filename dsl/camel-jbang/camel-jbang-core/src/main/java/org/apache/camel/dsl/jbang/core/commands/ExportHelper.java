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

package org.apache.camel.dsl.jbang.core.commands;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.apache.camel.util.IOHelper;

/**
 * Helper class provides export utilities for instance when copying files from temporary work folder to export directory
 * target.
 */
public final class ExportHelper {

    private ExportHelper() {
        // prevent instantiation of utility class.
    }

    public static void safeCopy(ClassLoader classLoader, String scheme, Path source, Path target, boolean override)
            throws Exception {
        safeCopy(classLoader, scheme, source, target, override, false);
    }

    public static void safeCopy(
            ClassLoader classLoader, String scheme, Path source, Path target, boolean override, boolean symbolicLink)
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
            safeCopy(source, target, override, symbolicLink);
        }
    }

    public static void safeCopy(Path source, Path target, boolean override) throws Exception {
        safeCopy(source, target, override, false);
    }

    public static void safeCopy(Path source, Path target, boolean override, boolean symbolicLink) throws Exception {
        if (!Files.exists(source)) {
            return;
        }

        if (Files.isDirectory(source)) {
            // flatten files if they are from a directory
            try (var stream = Files.list(source)) {
                stream.filter(Files::isRegularFile)
                        .forEach(child -> {
                            try {
                                safeCopy(child, target.resolve(child.getFileName()), override, symbolicLink);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            }
            return;
        }

        if (symbolicLink) {
            try {
                // must use absolute paths
                Path link = target.toAbsolutePath();
                Path src = source.toAbsolutePath();
                if (Files.exists(link)) {
                    Files.delete(link);
                }
                Files.createSymbolicLink(link, src);
                return; // success
            } catch (IOException e) {
                // ignore
            }
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
