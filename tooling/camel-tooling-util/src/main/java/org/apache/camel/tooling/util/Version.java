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

public final class Version implements Comparable<Version> {

    private final String version;

    public Version(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public String getMajor() {
        String[] thisParts = this.getVersion().split("\\.");
        return thisParts[0];
    }

    public String getMinor() {
        String[] thisParts = this.getVersion().split("\\.");
        if (thisParts.length > 1) {
            return thisParts[1];
        } else {
            return null;
        }
    }

    public String getPatch() {
        String[] thisParts = this.getVersion().split("\\.");
        if (thisParts.length > 2) {
            return thisParts[2];
        } else {
            return null;
        }
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
            long thisPart = i < thisParts.length ? Long.parseLong(thisParts[i]) : 0;
            long thatPart = i < thatParts.length ? Long.parseLong(thatParts[i]) : 0;
            if (thisPart < thatPart) {
                return -1;
            } else if (thisPart > thatPart) {
                return 1;
            }
        }
        return 0;
    }

    public String prevMinor() {
        String[] parts = this.getVersion().split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        int patch = parts.length == 3 ? Integer.parseInt(parts[2]) : 0;

        if (minor > 0) {
            minor -= 1;
        }

        return major + "." + minor + "." + patch;
    }

    @Override
    public String toString() {
        return version;
    }
}
