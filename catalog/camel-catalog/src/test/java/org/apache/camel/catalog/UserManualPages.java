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
package org.apache.camel.catalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The pages of the user manual, read from the source tree when the tests run inside the Camel repository (the manual is
 * not bundled in the catalog, unlike the component, data format, language and EIP pages).
 */
final class UserManualPages {

    private static final Path PAGES = Path.of("docs", "user-manual", "modules", "ROOT", "pages");

    private UserManualPages() {
    }

    /** The root of the Camel source tree, or null when the tests do not run inside it. */
    static Path repositoryRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve(PAGES)) && Files.isDirectory(dir.resolve("components"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        return null;
    }

    /**
     * The pages that document the current release, by name: the upgrade and migration guides are left out, as they show
     * the syntax of older releases on purpose.
     */
    static Map<String, String> currentPages() throws IOException {
        Map<String, String> answer = pages();
        answer.keySet().removeIf(name -> name.contains("upgrade-guide") || name.contains("migration-guide"));
        return answer;
    }

    /** The pages by name (the file name without .adoc), or an empty map outside the source tree. */
    static Map<String, String> pages() throws IOException {
        Map<String, String> answer = new TreeMap<>();
        Path root = repositoryRoot();
        if (root == null) {
            return answer;
        }
        try (Stream<Path> files = Files.walk(root.resolve(PAGES))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".adoc")).toList()) {
                String name = file.getFileName().toString();
                answer.put(name.substring(0, name.length() - 5), Files.readString(file));
            }
        }
        return answer;
    }
}
