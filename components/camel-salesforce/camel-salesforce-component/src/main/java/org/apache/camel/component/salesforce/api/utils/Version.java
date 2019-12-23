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
package org.apache.camel.component.salesforce.api.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Version implements Comparable<Version> {
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)");

    private final int major;

    private final int minor;

    private Version(final int major, final int minor) {
        this.major = major;
        this.minor = minor;
    }

    public static Version create(final String version) {
        final Matcher matcher = VERSION_PATTERN.matcher(version);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("API version needs to be in <number>.<number> format, given: " + version);
        }

        final int major = Integer.parseInt(matcher.group(1));
        final int minor = Integer.parseInt(matcher.group(2));

        return new Version(major, minor);
    }

    @Override
    public int compareTo(final Version other) {
        final int majorCompare = Integer.compare(major, other.major);

        if (majorCompare == 0) {
            return Integer.compare(minor, other.minor);
        } else {
            return majorCompare;
        }
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof Version)) {
            return false;
        }

        final Version other = (Version)obj;

        return compareTo(other) == 0;
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    @Override
    public int hashCode() {
        return 1 + 31 * (1 + 31 * major) + minor;
    }

    @Override
    public String toString() {
        return "v" + major + "." + minor;
    }

    public void requireAtLeast(final int requiredMajor, final int requiredMinor) {
        final Version required = new Version(requiredMajor, requiredMinor);

        if (this.compareTo(required) < 0) {
            throw new UnsupportedOperationException("This operation requires API version at least " + requiredMajor + "." + requiredMinor + ", currently configured for " + major
                                                    + "." + minor);
        }
    }
}
