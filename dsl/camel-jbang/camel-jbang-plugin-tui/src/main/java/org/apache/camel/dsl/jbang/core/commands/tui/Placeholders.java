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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The property placeholders of a label read from the sources ({@code timer:welcome (period={{welcome.period}})}) with
 * the values the running integration has for them, its profile applied: the properties of its status. A placeholder it
 * has no value for stays as written, or takes its default ({@code {{key:default}}}).
 */
final class Placeholders {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^{}:]+)(?::([^{}]*))?}}");

    private Placeholders() {
    }

    static String resolve(String text, IntegrationInfo info) {
        if (text == null || !text.contains("{{") || info == null) {
            return text;
        }
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = value(info, m.group(1).strip());
            if (value == null) {
                value = m.group(2) != null ? m.group(2) : m.group();
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String value(IntegrationInfo info, String key) {
        for (ConfigurationTab.ConfigProperty p : info.configProperties) {
            if (key.equals(p.key)) {
                return p.value;
            }
        }
        return null;
    }
}
