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
 * A saved example response stored against a Postman item.
 * <p>
 * These are the one place where a collection is richer than an OpenAPI specification: they carry a concrete status
 * code, headers and body rather than a schema, which makes them usable directly as mock responses.
 */
public final class PostmanResponse {

    private final JsonObject json;

    private PostmanResponse(JsonObject json) {
        this.json = json;
    }

    /**
     * Reads the {@code response} array of an item.
     *
     * @param  node the {@code response} node, may be {@code null}
     * @return      the saved examples in document order, never {@code null}
     */
    public static List<PostmanResponse> listFrom(Object node) {
        List<PostmanResponse> answer = new ArrayList<>();
        for (Object element : PostmanJson.asList(node)) {
            if (element instanceof JsonObject response) {
                answer.add(new PostmanResponse(response));
            }
        }
        return answer;
    }

    public String getName() {
        return PostmanJson.asString(json.get("name"));
    }

    /**
     * The HTTP status code, defaulting to 200 when the example does not record one.
     */
    public int getCode() {
        Object code = json.get("code");
        if (code instanceof Number n) {
            return n.intValue();
        }
        String text = PostmanJson.asString(code);
        if (text != null) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException e) {
                // fall through to the default
            }
        }
        return 200;
    }

    public List<PostmanKeyValue> getHeaders() {
        return PostmanKeyValue.listFrom(json, "header");
    }

    /**
     * Looks up a response header by name, case-insensitively, ignoring disabled entries.
     */
    public String getHeader(String name) {
        for (PostmanKeyValue header : getHeaders()) {
            if (!header.disabled() && header.key().equalsIgnoreCase(name)) {
                return header.value();
            }
        }
        return null;
    }

    public String getBody() {
        return PostmanJson.asString(json.get("body"));
    }

    public String getContentType() {
        return getHeader("Content-Type");
    }

    /**
     * Whether this example records a successful response, which is what a mock should replay by default.
     */
    public boolean isSuccess() {
        int code = getCode();
        return code >= 200 && code < 300;
    }
}
