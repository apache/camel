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

import org.apache.camel.util.json.JsonObject;

/**
 * The {@code body} of a Postman request.
 * <p>
 * Only enough of the body is modelled to infer a {@code Content-Type}. The body content itself is never sent: the
 * collection's body is sample data, and the message body of the exchange is what actually goes on the wire.
 */
public final class PostmanBody {

    public static final String MODE_RAW = "raw";
    public static final String MODE_URLENCODED = "urlencoded";
    public static final String MODE_FORMDATA = "formdata";
    public static final String MODE_FILE = "file";
    public static final String MODE_GRAPHQL = "graphql";

    private final JsonObject json;

    private PostmanBody(JsonObject json) {
        this.json = json;
    }

    /**
     * @param  node the {@code body} node, may be {@code null}
     * @return      the body, or {@code null} when there is none or it is disabled
     */
    public static PostmanBody parse(Object node) {
        JsonObject body = PostmanJson.asObject(node);
        if (body == null || body.isEmpty() || Boolean.TRUE.equals(body.get("disabled"))) {
            return null;
        }
        return new PostmanBody(body);
    }

    public String getMode() {
        return PostmanJson.asString(json.get("mode"));
    }

    public String getRaw() {
        return PostmanJson.asString(json.get("raw"));
    }

    /**
     * The field names declared for a {@code urlencoded} or {@code formdata} body, in document order.
     */
    public List<PostmanKeyValue> getFormFields() {
        String mode = getMode();
        if (MODE_URLENCODED.equals(mode) || MODE_FORMDATA.equals(mode)) {
            return PostmanKeyValue.listFrom(json, mode);
        }
        return List.of();
    }

    /**
     * Infers the {@code Content-Type} this request would send.
     * <p>
     * For a {@code raw} body the language recorded in {@code options.raw.language} is what Postman itself uses to
     * decide the content type, so it is honoured here too.
     *
     * @return the media type, or {@code null} when it cannot be inferred
     */
    public String inferContentType() {
        String mode = getMode();
        if (mode == null) {
            return null;
        }
        return switch (mode) {
            case MODE_RAW -> rawContentType();
            case MODE_GRAPHQL -> "application/json";
            case MODE_URLENCODED -> "application/x-www-form-urlencoded";
            case MODE_FORMDATA -> "multipart/form-data";
            case MODE_FILE -> "application/octet-stream";
            default -> null;
        };
    }

    private String rawContentType() {
        JsonObject options = PostmanJson.asObject(json.get("options"));
        JsonObject raw = options != null ? PostmanJson.asObject(options.get("raw")) : null;
        String language = raw != null ? PostmanJson.asString(raw.get("language")) : null;
        if (language == null) {
            return "text/plain";
        }
        return switch (language.toLowerCase()) {
            case "json" -> "application/json";
            case "xml" -> "application/xml";
            case "html" -> "text/html";
            case "javascript" -> "application/javascript";
            default -> "text/plain";
        };
    }
}
