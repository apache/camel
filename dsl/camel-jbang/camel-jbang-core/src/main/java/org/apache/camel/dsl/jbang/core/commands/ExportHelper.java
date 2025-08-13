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

/**
 * Helper class provides export utilities for instance when copying files from temporary work folder to export directory
 * target.
 */
public final class ExportHelper {

    private ExportHelper() {
        // prevent instantiation of utility class.
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
