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

import org.apache.camel.tooling.model.ApiMethodModel;
import org.apache.camel.tooling.model.ApiModel;

public final class MvelHelper {

    public static final MvelHelper INSTANCE = new MvelHelper();

    private static final Pattern CURLY_BRACKET_ESCAPE = Pattern.compile("(\\{[a-zA-Z0-9]+?)\\}");

    private static final Pattern URL_ESCAPE = Pattern.compile("(?<!href=\")(http(:?s)?://|(:?s)?ftp(?:s)?://)");

    private MvelHelper() {
        // utility class
    }

    public static String escape(final String raw) {
        if (raw == null) {
            return null;
        }

        final String escapedCurlyBrackets = CURLY_BRACKET_ESCAPE.matcher(raw).replaceAll("\\\\$1\\}");

        return URL_ESCAPE.matcher(escapedCurlyBrackets).replaceAll("\\\\$1");
    }

    public static String componentName(String scheme) {
        String text = SchemaHelper.dashToCamelCase(scheme);
        // first char should be upper cased
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    public static String formatSignature(String signature) {
        signature = signature.replace('$', '.');
        return signature + ";";
    }

    public static String apiMethodAlias(ApiModel api, ApiMethodModel method) {
        String name = method.getName();
        for (String alias : api.getAliases()) {
            int pos = alias.indexOf('=');
            String pattern = alias.substring(0, pos);
            String aliasMethod = alias.substring(pos + 1);
            // match ignore case
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(name).matches()) {
                return aliasMethod;
            }
        }
        // empty if no alias
        return "";
    }

    public static String producerOrConsumer(ApiModel api) {
        if (api.isConsumerOnly()) {
            return "Consumer";
        } else if (api.isProducerOnly()) {
            return "Producer";
        }
        return "Both";
    }

}
