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
package org.apache.camel.component.rest.swagger;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.util.StringHelper;

import static org.apache.camel.util.StringHelper.notEmpty;

final class RestSwaggerHelper {

    private static final Pattern HOST_PATTERN = Pattern.compile("https?://[^:]+(:\\d+)?", Pattern.CASE_INSENSITIVE);

    private RestSwaggerHelper() {
        // utility class
    }

    public static String isMediaRange(final String given, final String name) {
        return notEmpty(given, name);
    }

    static String isHostParam(final String given) {
        final String hostUri = StringHelper.notEmpty(given, "host");

        final Matcher matcher = HOST_PATTERN.matcher(given);

        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                "host must be an absolute URI (e.g. http://api.example.com), given: `" + hostUri + "`");
        }

        return hostUri;
    }
}
