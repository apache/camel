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
package org.apache.camel.maven.packaging;

import java.util.regex.Pattern;

public final class MvelHelper {

    public static final MvelHelper INSTANCE = new MvelHelper();

    private static final Pattern DOLLAR_ESCAPE = Pattern.compile("\\$");

    private static final Pattern CURLY_BRACKET_ESCAPE = Pattern.compile("(\\{[a-zA-Z0-9]+?)\\}");

    private static final Pattern URL_ESCAPE = Pattern.compile("(?<!href=\")(http(:?s)?://|(:?s)?ftp(?:s)?)");

    private MvelHelper() {
        // utility class
    }

    public static String escape(final String raw) {
        if (raw == null) {
            return null;
        }

        final String escapedDollars = DOLLAR_ESCAPE.matcher(raw).replaceAll("\\\\\\$");
        final String escapedCurlyBrackets = CURLY_BRACKET_ESCAPE.matcher(escapedDollars).replaceAll("\\\\$1\\\\}");
        final String escapedUrls = URL_ESCAPE.matcher(escapedCurlyBrackets).replaceAll("\\\\$1");

        return escapedUrls;
    }
}
