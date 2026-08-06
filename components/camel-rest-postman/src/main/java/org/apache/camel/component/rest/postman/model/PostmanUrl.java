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
package org.apache.camel.component.rest.postman.model;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.util.json.JsonObject;

/**
 * The {@code url} of a Postman request.
 * <p>
 * The accessors deliberately return the values <em>as written in the collection</em>, so they may still contain
 * {@code {{variable}}} placeholders and {@code :pathParam} markers. Resolving those is the caller's job, because
 * substitution needs the variable scope of the enclosing folders, which a URL does not know about.
 */
public final class PostmanUrl {

    private final String protocol;
    private final String host;
    private final String port;
    private final List<String> pathSegments;
    private final List<PostmanKeyValue> queryParams;
    private final List<PostmanKeyValue> pathVariables;
    private final String raw;

    private PostmanUrl(String protocol, String host, String port, List<String> pathSegments,
                       List<PostmanKeyValue> queryParams, List<PostmanKeyValue> pathVariables, String raw) {
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.pathSegments = List.copyOf(pathSegments);
        this.queryParams = List.copyOf(queryParams);
        this.pathVariables = List.copyOf(pathVariables);
        this.raw = raw;
    }

    /**
     * Reads a {@code url} node, which the schema allows to be either a string or an object.
     * <p>
     * When the node is an object that carries only {@code raw}, the raw form is parsed, so that a collection written in
     * either style yields the same structure.
     *
     * @param  node the {@code url} node, may be {@code null}
     * @return      the parsed URL, never {@code null}
     */
    public static PostmanUrl parse(Object node) {
        if (node == null) {
            return new PostmanUrl(null, null, null, List.of(), List.of(), List.of(), null);
        }
        if (node instanceof String s) {
            return parseRaw(s, List.of());
        }
        JsonObject url = PostmanJson.asObject(node);
        if (url == null) {
            return new PostmanUrl(null, null, null, List.of(), List.of(), List.of(), null);
        }

        List<PostmanKeyValue> variables = PostmanKeyValue.listFrom(url, "variable");
        String rawValue = PostmanJson.asString(url.get("raw"));

        boolean structured = url.get("host") != null || url.get("path") != null;
        if (!structured && rawValue != null) {
            // only the raw form was given, so recover the structure from it but keep any declared path variables
            return parseRaw(rawValue, variables);
        }

        String hostValue = String.join(".", PostmanJson.asStringList(url.get("host"), "."));
        List<String> segments = PostmanJson.asStringList(url.get("path"), "/");

        return new PostmanUrl(
                PostmanJson.asString(url.get("protocol")),
                hostValue.isEmpty() ? null : hostValue,
                PostmanJson.asString(url.get("port")),
                segments,
                PostmanKeyValue.listFrom(url, "query"),
                variables,
                rawValue);
    }

    /**
     * Parses a raw URL string leniently.
     * <p>
     * {@link java.net.URI} cannot be used here: a raw Postman URL is routinely not a valid URI, because it starts with
     * a {@code {{baseUrl}}} placeholder. So the string is split structurally instead of being validated.
     */
    private static PostmanUrl parseRaw(String rawUrl, List<PostmanKeyValue> declaredVariables) {
        String remainder = rawUrl.trim();

        // a fragment is never sent over the wire, so it is recorded only as part of the raw form
        int hash = remainder.indexOf('#');
        if (hash >= 0) {
            remainder = remainder.substring(0, hash);
        }

        String query = null;
        int questionMark = remainder.indexOf('?');
        if (questionMark >= 0) {
            query = remainder.substring(questionMark + 1);
            remainder = remainder.substring(0, questionMark);
        }

        String protocol = null;
        int schemeSeparator = remainder.indexOf("://");
        if (schemeSeparator > 0) {
            protocol = remainder.substring(0, schemeSeparator);
            remainder = remainder.substring(schemeSeparator + 3);
        }

        String authority;
        String path;
        if (remainder.startsWith("/")) {
            // a host-relative URL, so there is no authority to split off
            authority = "";
            path = remainder;
        } else {
            int slash = remainder.indexOf('/');
            authority = slash >= 0 ? remainder.substring(0, slash) : remainder;
            path = slash >= 0 ? remainder.substring(slash) : "";
        }

        String host = authority;
        String port = null;
        int colon = authority.lastIndexOf(':');
        if (colon >= 0 && isAllDigits(authority.substring(colon + 1))) {
            host = authority.substring(0, colon);
            port = authority.substring(colon + 1);
        }

        List<String> segments = new ArrayList<>();
        for (String segment : path.split("/")) {
            if (!segment.isEmpty()) {
                segments.add(segment);
            }
        }

        return new PostmanUrl(
                protocol, host.isEmpty() ? null : host, port, segments,
                parseRawQuery(query), declaredVariables, rawUrl);
    }

    private static List<PostmanKeyValue> parseRawQuery(String query) {
        List<PostmanKeyValue> answer = new ArrayList<>();
        if (query == null || query.isEmpty()) {
            return answer;
        }
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String key = equals >= 0 ? pair.substring(0, equals) : pair;
            String value = equals >= 0 ? pair.substring(equals + 1) : null;
            if (!key.isEmpty()) {
                answer.add(new PostmanKeyValue(key, value, false, null));
            }
        }
        return answer;
    }

    private static boolean isAllDigits(String text) {
        if (text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public String getProtocol() {
        return protocol;
    }

    /**
     * The host, with an array of labels already joined by {@code .}. May be a bare {@code {{baseUrl}}} placeholder that
     * expands to a complete URL, which callers must re-parse after substitution.
     */
    public String getHost() {
        return host;
    }

    public String getPort() {
        return port;
    }

    /**
     * The path segments, without separators, still carrying {@code :name} markers for path parameters.
     */
    public List<String> getPathSegments() {
        return pathSegments;
    }

    public List<PostmanKeyValue> getQueryParams() {
        return queryParams;
    }

    /**
     * The values declared in {@code url.variable}, which supply defaults for the {@code :name} path markers.
     */
    public List<PostmanKeyValue> getPathVariables() {
        return pathVariables;
    }

    public String getRaw() {
        return raw;
    }
}
