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
package org.apache.camel.component.rest.postman.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.camel.util.SensitiveUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * Removes credentials from a collection document before it is served over HTTP.
 * <p>
 * Serving the collection publishes route-author configuration to whoever can reach the API context path, and a real
 * collection routinely carries live tokens in its {@code auth} blocks, in its variables, in header values and in
 * request URLs. Redaction is therefore unconditional rather than an option:
 * <ul>
 * <li>every {@code auth} block is removed</li>
 * <li>the value of a variable of type {@code secret} is masked, and so is the value of any other variable whose name
 * marks a credential (such as {@code apiKey} or {@code password})</li>
 * <li>the value of a header (in requests and in example responses) whose name marks a credential, such as
 * {@code Authorization}, {@code x-api-key}, {@code Cookie} or any name that contains {@code token}, is masked, and so
 * are the {@code urlencoded} and {@code formdata} body fields whose name marks a credential</li>
 * <li>the value of every cookie of an example response is masked</li>
 * <li>in a URL, the password of the userinfo ({@code user:password@host}) and the value of a query parameter whose name
 * marks a credential (such as {@code api_key} or {@code access_token}) are masked, in the {@code raw} URL as well as in
 * the structured {@code host} and {@code query} parts</li>
 * </ul>
 * A value that is only a Postman variable reference, such as <code>{{apiKey}}</code> or <code>Bearer {{token}}</code>,
 * is a placeholder rather than a credential and is left as it is, so the served collection still shows which variable a
 * client must provide. The variable it refers to is masked where it is defined, when it holds a literal credential.
 */
public final class PostmanRedactor {

    private static final String REDACTED = "***";

    // the lists of {key, value} objects whose value is masked when the key marks a credential
    private static final Set<String> KEY_VALUE_LISTS = Set.of("header", "query", "urlencoded", "formdata");

    // words that mark a credential anywhere in a name (in lower case without dashes and underscores), in addition to
    // the names that SensitiveUtils.isSensitiveHeader detects, such as X-Token-Id
    private static final String[] SENSITIVE_WORDS = { "token", "password", "secret" };

    // a variable reference, optionally after an auth scheme, such as {{apiKey}} or Bearer {{token}}
    private static final Pattern PLACEHOLDER = Pattern.compile("(?:[A-Za-z][A-Za-z0-9-]*\\s+)?\\{\\{[^{}]+}}");

    private PostmanRedactor() {
    }

    /**
     * Returns a deep copy of the document with every {@code auth} block removed and the credentials in variables,
     * headers, body fields, cookies and URLs masked.
     */
    public static JsonObject redact(JsonObject document) {
        return (JsonObject) redactNode(document, null);
    }

    private static Object redactNode(Object node, String key) {
        if (node instanceof JsonObject object) {
            JsonObject answer = new JsonObject();
            for (var entry : object.entrySet()) {
                String name = entry.getKey();
                if ("auth".equals(name)) {
                    // drop the block entirely rather than blanking its values, so that neither the credential nor
                    // the fact that one is configured is disclosed
                    continue;
                }
                answer.put(name, redactNode(entry.getValue(), name));
            }
            if ("url".equals(key)) {
                redactUrlObject(answer);
            }
            return answer;
        }
        if (node instanceof List<?> list) {
            List<Object> answer = new ArrayList<>(list.size());
            for (Object element : list) {
                Object redacted = redactNode(element, null);
                if (redacted instanceof JsonObject object) {
                    if ("variable".equals(key)) {
                        redactVariable(object);
                    } else if ("cookie".equals(key)) {
                        maskValue(object);
                    } else if (KEY_VALUE_LISTS.contains(key) && isSensitiveName(object.get("key"))) {
                        maskValue(object);
                    }
                }
                answer.add(redacted);
            }
            return new JsonArray(answer);
        }
        if (node instanceof String text) {
            if ("url".equals(key)) {
                return redactUrl(text);
            }
            if ("header".equals(key)) {
                // the headers can also be given as a single string of "Name: value" lines
                return redactHeaderLines(text);
            }
        }
        return node;
    }

    private static void redactVariable(JsonObject variable) {
        if (!variable.containsKey("value")) {
            return;
        }
        if ("secret".equals(variable.get("type"))) {
            variable.put("value", REDACTED);
        } else if (isSensitiveName(variable.get("key"))) {
            maskValue(variable);
        } else if (variable.get("value") instanceof String value) {
            // a variable such as baseUrl can hold a URL with credentials in it
            variable.put("value", redactUrl(value));
        }
    }

    private static void redactUrlObject(JsonObject url) {
        if (url.get("raw") instanceof String raw) {
            url.put("raw", redactUrl(raw));
        }
        // the structured form keeps the userinfo in the first element of the host, such as user:password@example
        if (url.get("host") instanceof List<?> host) {
            List<Object> answer = new ArrayList<>(host.size());
            for (Object element : host) {
                answer.add(element instanceof String text && text.indexOf('@') != -1 ? redactHost(text) : element);
            }
            url.put("host", new JsonArray(answer));
        }
    }

    private static String redactHost(String host) {
        // the userinfo rule of SensitiveUtils needs a scheme in front of the userinfo
        String prefix = "x://";
        return maskUserInfo(prefix + host).substring(prefix.length());
    }

    /**
     * Masks the userinfo password, and the values of the query parameters whose name marks a credential, in the URL.
     */
    static String redactUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        String answer = maskUserInfo(url);
        int query = answer.indexOf('?');
        if (query == -1) {
            return answer;
        }
        int fragment = answer.indexOf('#', query);
        String params = fragment == -1 ? answer.substring(query + 1) : answer.substring(query + 1, fragment);
        StringBuilder sb = new StringBuilder(answer.length());
        sb.append(answer, 0, query + 1);
        String[] parts = params.split("&", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append('&');
            }
            String part = parts[i];
            int eq = part.indexOf('=');
            if (eq > 0 && isSensitiveName(part.substring(0, eq)) && isLiteral(part.substring(eq + 1))) {
                sb.append(part, 0, eq + 1).append(REDACTED);
            } else {
                sb.append(part);
            }
        }
        if (fragment != -1) {
            sb.append(answer, fragment, answer.length());
        }
        return sb.toString();
    }

    private static String maskUserInfo(String url) {
        String masked = SensitiveUtils.maskUserInfoCredentials(url, REDACTED);
        if (masked.equals(url)) {
            return url;
        }
        // keep the userinfo when its password is only a placeholder: find the part that was replaced
        int max = Math.min(url.length(), masked.length());
        int start = 0;
        while (start < max && url.charAt(start) == masked.charAt(start)) {
            start++;
        }
        int end = 0;
        while (end < max - start && url.charAt(url.length() - 1 - end) == masked.charAt(masked.length() - 1 - end)) {
            end++;
        }
        String password = url.substring(start, url.length() - end);
        return isPlaceholder(password) ? url : masked;
    }

    private static String redactHeaderLines(String text) {
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            String line = lines[i];
            int colon = line.indexOf(':');
            if (colon > 0 && isSensitiveName(line.substring(0, colon)) && isLiteral(line.substring(colon + 1))) {
                sb.append(line, 0, colon + 1).append(' ').append(REDACTED);
            } else {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private static void maskValue(JsonObject object) {
        Object value = object.get("value");
        if (value instanceof String text ? isLiteral(text) : value != null) {
            object.put("value", REDACTED);
        }
    }

    /**
     * Whether the name of a header, variable, query parameter or form field marks a credential.
     */
    static boolean isSensitiveName(Object name) {
        if (!(name instanceof String text) || text.isBlank()) {
            return false;
        }
        text = text.strip();
        if (SensitiveUtils.isSensitiveHeader(text)) {
            return true;
        }
        String key = text.toLowerCase(Locale.ENGLISH).replace("-", "").replace("_", "");
        for (String word : SENSITIVE_WORDS) {
            if (key.contains(word)) {
                return true;
            }
        }
        return false;
    }

    // a literal value is one that is neither blank nor only a variable reference
    private static boolean isLiteral(String value) {
        return !value.isBlank() && !isPlaceholder(value);
    }

    private static boolean isPlaceholder(String value) {
        return PLACEHOLDER.matcher(value.strip()).matches();
    }
}
