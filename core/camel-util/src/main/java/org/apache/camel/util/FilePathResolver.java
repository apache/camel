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
package org.apache.camel.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A resolver for file paths that supports resolving with system and environment properties.
 */
public final class FilePathResolver {

    // must be non greedy patterns
    private static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{env:(.*?)\\}", Pattern.DOTALL);
    private static final Pattern SYS_PATTERN = Pattern.compile("\\$\\{(.*?)\\}", Pattern.DOTALL);

    private FilePathResolver() {
    }

    /**
     * Resolves the path.
     * <p/>
     * The pattern is:
     * <ul>
     *   <li><tt>${env.key}</tt> for environment variables.</li>
     *   <li><tt>${key}</tt> for JVM system properties.</li>
     * </ul>
     * For example: <tt>${env.KARAF_HOME}/data/logs</tt>
     *
     * @param path  the path
     * @return the resolved path
     * @throws IllegalArgumentException is thrown if system property / environment not found
     */
    public static String resolvePath(String path) throws IllegalArgumentException {
        Matcher matcher = ENV_PATTERN.matcher(path);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = System.getenv(key);
            if (ObjectHelper.isEmpty(value)) {
                throw new IllegalArgumentException("Cannot find system environment with key: " + key);
            }
            // must quote the replacement to have it work as literal replacement
            value = Matcher.quoteReplacement(value);
            path = matcher.replaceFirst(value);
            // must match again as location is changed
            matcher = ENV_PATTERN.matcher(path);
        }

        matcher = SYS_PATTERN.matcher(path);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = System.getProperty(key);
            if (ObjectHelper.isEmpty(value)) {
                throw new IllegalArgumentException("Cannot find JVM system property with key: " + key);
            }
            // must quote the replacement to have it work as literal replacement
            value = Matcher.quoteReplacement(value);
            path = matcher.replaceFirst(value);
            // must match again as location is changed
            matcher = SYS_PATTERN.matcher(path);
        }

        return path;
    }

}
