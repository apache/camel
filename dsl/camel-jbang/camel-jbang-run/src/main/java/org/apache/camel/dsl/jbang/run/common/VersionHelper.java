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
package org.apache.camel.dsl.jbang.run.common;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.catalog.cve.AdvisoryVersions;

public final class VersionHelper {

    // the major version a version starts with (up to 9 digits, so it always fits an int)
    private static final Pattern LEADING_MAJOR = Pattern.compile("^\\d{1,9}");

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
                homeDir = CamelHome.getHomeDir().toString();
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
        return AdvisoryVersions.isGE(source, target);
    }

    public static boolean isLE(String source, String target) {
        return AdvisoryVersions.isLE(source, target);
    }

    public static boolean isBetween(String source, String inclusive, String exclusive) {
        return AdvisoryVersions.isBetween(source, inclusive, exclusive);
    }

    /**
     * Compares two versions part by part as numbers, such as 3.27.3.1 before 3.27.4, and 4.100 after 4.99. With the
     * same numbers, a pre-release (such as 4.24.0-SNAPSHOT, -RC1 or -M2) comes before the release, and another
     * qualifier (such as a build number) after it, as in Maven.
     *
     * @return a negative number, zero or a positive number as the source is before, the same as, or after the target
     *         (zero when one of them is missing)
     */
    public static int compare(String source, String target) {
        return AdvisoryVersions.compare(source, target);
    }

    /**
     * Whether the Camel version is 4.0 or newer, including its milestones (4.0.0-M1) and the builds of a vendor
     * (4.10.3.redhat-00001). The Camel CLI runs Camel 4 only. A version that does not start with a number is not
     * rejected here.
     */
    public static boolean isCamel4OrNewer(String version) {
        if (version == null) {
            return true;
        }
        Matcher m = LEADING_MAJOR.matcher(version.trim());
        return !m.find() || Integer.parseInt(m.group()) >= 4;
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
