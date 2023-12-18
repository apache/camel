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

package org.apache.camel.dsl.jbang.core.commands.k;

import java.util.Arrays;
import java.util.Locale;

/**
 * Supported set of file resource and URL schemes that may be used to resolve an integration resource (e.g. source
 * file).
 */
public enum SourceScheme {

    GIST("https://gist.github"),
    GITHUB("https://github.com/"),
    RAW_GITHUB("https://raw.githubusercontent.com/"),
    FILE,
    CLASSPATH,
    HTTP,
    HTTPS,
    UNKNOWN;

    private final String uri;

    SourceScheme() {
        this(null);
    }

    SourceScheme(String uri) {
        this.uri = uri;
    }

    /**
     * Try to resolve source scheme from given file path URL. Checks for special GIST and GITHUB endpoint URLs. By
     * default, uses unknown scheme usually leads to loading resource from file system.
     *
     * @param  path
     * @return
     */
    public static SourceScheme fromUri(String path) {
        return Arrays.stream(values())
                .filter(scheme -> path.startsWith(scheme.name().toLowerCase(Locale.US) + ":") ||
                        (scheme.uri != null && path.startsWith(scheme.uri)))
                .findFirst()
                .orElse(UNKNOWN); // use file as default scheme
    }

    /**
     * If any strip scheme prefix from given name.
     *
     * @param  name
     * @return
     */
    public static String onlyName(String name) {
        for (SourceScheme scheme : values()) {
            if (name.startsWith(scheme.name().toLowerCase(Locale.US) + ":")) {
                return name.substring(scheme.name().length() + 1);
            }
        }

        return name;
    }
}
