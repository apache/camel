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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VersionHelper {

    private static final Pattern LEADING_NUMBER = Pattern.compile("^\\d{1,18}");

    private VersionHelper() {
    }

    public static String getJBangVersion() {
        try {
            // find actual version in JBANG_HOME
            String homeDir = System.getenv("JBANG_HOME");
            String path = "";
            if (homeDir == null || homeDir.isBlank()) {
                // fallback to .jbang cache that has a list of latest version
                path = ".jbang/cache/";
                homeDir = CommandLineHelper.getHomeDir().toString();
            }
            Path file = Paths.get(homeDir).resolve(path + "version.txt");
            if (Files.exists(file) && Files.isRegularFile(file)) {
                String text = Files.readString(file);
                text = text.trim();
                return text;
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    public static boolean isGE(String source, String target) {
        return compare(source, target) >= 0;
    }

    public static boolean isLE(String source, String target) {
        return compare(source, target) <= 0;
    }

    public static boolean isBetween(String source, String inclusive, String exclusive) {
        return compare(source, inclusive) >= 0 && compare(source, exclusive) < 0;
    }

    /**
     * Compares two versions part by part as numbers, such as 3.27.3.1 before 3.27.4, and 4.100 after 4.99. With the
     * same numbers, a version with a qualifier (such as 4.24.0-SNAPSHOT) comes before the release, as in Maven.
     *
     * @return a negative number, zero or a positive number as the source is before, the same as, or after the target
     *         (zero when one of them is missing)
     */
    public static int compare(String source, String target) {
        if (source == null || target == null || source.isBlank() || target.isBlank()) {
            return 0;
        }
        List<Long> s = new ArrayList<>();
        String sq = parse(source, s);
        List<Long> t = new ArrayList<>();
        String tq = parse(target, t);
        for (int i = 0; i < Math.max(s.size(), t.size()); i++) {
            long a = i < s.size() ? s.get(i) : 0;
            long b = i < t.size() ? t.get(i) : 0;
            if (a != b) {
                return Long.compare(a, b);
            }
        }
        if (sq.isEmpty() != tq.isEmpty()) {
            // the release comes after its snapshot
            return sq.isEmpty() ? 1 : -1;
        }
        return sq.compareTo(tq);
    }

    /**
     * The numbers of the version (separated by dots or commas), and what follows them, such as -SNAPSHOT.
     */
    private static String parse(String version, List<Long> numbers) {
        String[] parts = version.trim().split("[.,]");
        for (int i = 0; i < parts.length; i++) {
            Matcher m = LEADING_NUMBER.matcher(parts[i]);
            if (!m.find()) {
                return String.join(".", Arrays.copyOfRange(parts, i, parts.length));
            }
            numbers.add(Long.parseLong(m.group()));
            if (m.end() < parts[i].length()) {
                // such as 0-SNAPSHOT
                String rest = parts[i].substring(m.end());
                return i + 1 < parts.length
                        ? rest + "." + String.join(".", Arrays.copyOfRange(parts, i + 1, parts.length)) : rest;
            }
        }
        return "";
    }

    public static String getMajorMinorVersion(String version) {
        String[] parts = version.split("\\.");
        return parts[0] + "." + parts[1];
    }

    public static String extractCamelVersion() {
        return org.apache.camel.main.util.VersionHelper.extractCamelVersion();
    }

    public static String extractKameletsVersion() {
        return org.apache.camel.main.util.VersionHelper.extractKameletsVersion();
    }

    public static void setCamelVersion(String version) {
        org.apache.camel.main.util.VersionHelper.setCamelVersion(version);
    }
}
