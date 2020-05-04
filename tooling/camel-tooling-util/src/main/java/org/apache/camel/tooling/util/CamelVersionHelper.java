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

/**
 * A simple util to test Camel versions.
 *
 * This implementation only checks the numeric values and disregard SNAPSHOT or any other qualifiers.
 */
public final class CamelVersionHelper {

    private CamelVersionHelper() {
        // utility class, never constructed
    }

    /**
     * Checks whether other >= base
     *
     * @param base  the base version
     * @param other the other version
     * @return <tt>true</tt> if GE, <tt>false</tt> otherwise
     */
    public static boolean isGE(String base, String other) {
        if (base == null || base.isEmpty()) {
            throw new IllegalArgumentException("Empty base version");
        }
        if (other == null || other.isEmpty()) {
            throw new IllegalArgumentException("Empty other version");
        }

        // strip suffix/qualifier as we dont support that
        base = base.replaceAll("[^\\d|^\\.]", "");
        other = other.replaceAll("[^\\d|^\\.]", "");

        if (!base.matches("[0-9]+(\\.[0-9]+)*")) {
            throw new IllegalArgumentException("Invalid version format");
        }
        if (!other.matches("[0-9]+(\\.[0-9]+)*")) {
            throw new IllegalArgumentException("Invalid version format");
        }

        Version ver1 = new Version(base);
        Version ver2 = new Version(other);
        return ver2.compareTo(ver1) >= 0;
    }

    private static final class Version implements Comparable<Version> {

        private final String version;

        private Version(String version) {
            this.version = version;
        }

        private String getVersion() {
            return version;
        }

        @Override
        public int compareTo(Version that) {
            if (that == null) {
                return 1;
            }
            String[] thisParts = this.getVersion().split("\\.");
            String[] thatParts = that.getVersion().split("\\.");
            int length = Math.max(thisParts.length, thatParts.length);
            for (int i = 0; i < length; i++) {
                int thisPart = i < thisParts.length ? Integer.parseInt(thisParts[i]) : 0;
                int thatPart = i < thatParts.length ? Integer.parseInt(thatParts[i]) : 0;
                if (thisPart < thatPart) {
                    return -1;
                } else if (thisPart > thatPart) {
                    return 1;
                }
            }
            return 0;
        }

    }

}
