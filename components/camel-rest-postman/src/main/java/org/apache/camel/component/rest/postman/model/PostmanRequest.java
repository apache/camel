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

import java.util.List;
import java.util.Locale;

import org.apache.camel.util.json.JsonObject;

/**
 * The {@code request} of a Postman item.
 */
public final class PostmanRequest {

    private static final String DEFAULT_METHOD = "GET";

    private final JsonObject json;
    private final PostmanUrl url;

    private PostmanRequest(JsonObject json) {
        this.json = json;
        this.url = PostmanUrl.parse(json.get("url"));
    }

    /**
     * Reads a {@code request} node.
     * <p>
     * The schema permits the shorthand form where the whole request is a bare URL string, which implies a GET. That
     * form is expanded here so the rest of the component only ever sees the object form.
     *
     * @param  node the {@code request} node, may be {@code null}
     * @return      the request, or {@code null} when the node is absent, which marks the item as a folder
     */
    public static PostmanRequest parse(Object node) {
        if (node instanceof String s) {
            JsonObject synthetic = new JsonObject();
            synthetic.put("method", DEFAULT_METHOD);
            synthetic.put("url", s);
            return new PostmanRequest(synthetic);
        }
        JsonObject request = PostmanJson.asObject(node);
        return request != null ? new PostmanRequest(request) : null;
    }

    /**
     * The HTTP method, upper-cased, defaulting to {@code GET} when the collection omits it.
     */
    public String getMethod() {
        String method = PostmanJson.asString(json.get("method"));
        if (method == null || method.isBlank()) {
            return DEFAULT_METHOD;
        }
        return method.trim().toUpperCase(Locale.ROOT);
    }

    public PostmanUrl getUrl() {
        return url;
    }

    /**
     * The declared request headers, including disabled ones, which callers are expected to filter out.
     */
    public List<PostmanKeyValue> getHeaders() {
        return PostmanKeyValue.listFrom(json, "header");
    }

    /**
     * Looks up a declared header by name, case-insensitively, ignoring disabled entries.
     *
     * @return the value, or {@code null} when the header is not declared
     */
    public String getHeader(String name) {
        for (PostmanKeyValue header : getHeaders()) {
            if (!header.disabled() && header.key().equalsIgnoreCase(name)) {
                return header.value();
            }
        }
        return null;
    }

    public PostmanBody getBody() {
        return PostmanBody.parse(json.get("body"));
    }

    public PostmanAuth getAuth() {
        return PostmanAuth.parse(json.get("auth"));
    }

    public String getDescription() {
        Object description = json.get("description");
        if (description instanceof JsonObject o) {
            return PostmanJson.asString(o.get("content"));
        }
        return PostmanJson.asString(description);
    }
}
