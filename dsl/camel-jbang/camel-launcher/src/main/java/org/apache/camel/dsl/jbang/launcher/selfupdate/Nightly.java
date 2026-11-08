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
package org.apache.camel.dsl.jbang.launcher.selfupdate;

import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.dsl.jbang.core.common.InstallDetector;

/**
 * The versions of the nightly builds of the launcher, which Apache publishes to its snapshot repository: a snapshot
 * version ({@code 4.24.0-SNAPSHOT}), the newest snapshot of main ({@code SNAPSHOT}), and one nightly build, named as
 * the snapshot repository names it ({@code 4.24.0-20261108.143653-24}: the timestamp and build number of the deploy).
 * <p>
 * A nightly build is not an Apache release. The web installer installs each nightly build in its own directory, named
 * after it, so a command that still runs from the previous build keeps its files.
 */
public final class Nightly {

    /** The selector for the newest snapshot of main. */
    public static final String LATEST = "SNAPSHOT";

    private static final Pattern SNAPSHOT_VERSION = Pattern.compile("([0-9]+\\.[0-9]+\\.[0-9]+)-SNAPSHOT");
    private static final Pattern BUILD = Pattern.compile("([0-9]+\\.[0-9]+\\.[0-9]+)-([0-9]{8}\\.[0-9]{6})-([0-9]{1,9})");

    private Nightly() {
    }

    /** Whether {@code value} is a snapshot version, {@code X.Y.Z-SNAPSHOT}. */
    public static boolean isSnapshotVersion(String value) {
        return value != null && SNAPSHOT_VERSION.matcher(value).matches();
    }

    /** Whether {@code value} is the selector of the newest snapshot of main, {@code SNAPSHOT} (case-insensitive). */
    public static boolean isLatest(String value) {
        return LATEST.equalsIgnoreCase(value);
    }

    /** Whether {@code value} is one nightly build, {@code X.Y.Z-YYYYMMDD.HHMMSS-N}. */
    public static boolean isBuild(String value) {
        return value != null && BUILD.matcher(value).matches();
    }

    /**
     * Whether {@code value} asks for nightly builds: {@code SNAPSHOT}, {@code X.Y.Z-SNAPSHOT} or one nightly build.
     */
    public static boolean isNightly(String value) {
        return isLatest(value) || isSnapshotVersion(value) || isBuild(value);
    }

    /** Normalizes a nightly selector: {@code snapshot} becomes {@code SNAPSHOT}, the others are kept. */
    public static String normalize(String value) {
        return isLatest(value) ? LATEST : value;
    }

    /** The snapshot version of a nightly build: {@code 4.24.0-SNAPSHOT} for {@code 4.24.0-20261108.143653-24}. */
    public static String snapshotVersionOf(String build) {
        Matcher m = BUILD.matcher(build);
        if (!m.matches()) {
            throw new IllegalArgumentException("not a nightly build: " + build);
        }
        return m.group(1) + "-SNAPSHOT";
    }

    /**
     * Compares two nightly builds of the same snapshot version by their deploy (timestamp, then build number); a
     * positive value when {@code a} is newer.
     */
    public static int compare(String a, String b) {
        Matcher ma = BUILD.matcher(a);
        Matcher mb = BUILD.matcher(b);
        if (!ma.matches() || !mb.matches()) {
            throw new IllegalArgumentException("not nightly builds: " + a + ", " + b);
        }
        int c = ma.group(2).compareTo(mb.group(2));
        return c != 0 ? c : Integer.compare(Integer.parseInt(ma.group(3)), Integer.parseInt(mb.group(3)));
    }

    /**
     * The nightly build the launcher runs from: the name of its directory under the web installer's {@code versions}
     * directory, when that is a nightly build.
     */
    public static Optional<String> runningBuild(Path launcherJar, Path versionsRoot) {
        return InstallDetector.nightlyBuildOf(launcherJar, versionsRoot);
    }

    /**
     * Describes a nightly selector for messages: {@code the nightly builds of main}, {@code ... of 4.24.0-SNAPSHOT}.
     */
    public static String describe(String selector) {
        if (isLatest(selector)) {
            return "the nightly builds of main";
        }
        if (isSnapshotVersion(selector)) {
            return "the nightly builds of " + selector;
        }
        return "nightly build " + selector;
    }

    /** The notice printed before a nightly build is installed. */
    public static String notice(String build) {
        return "Note: " + build + " is a nightly development build of Apache Camel, for testing only. It is not an Apache"
               + " release, and is verified only against the SHA-1 published next to it in the snapshot repository.";
    }
}
