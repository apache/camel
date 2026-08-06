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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.util.json.JsonObject;

/**
 * The root of a Postman Collection v2.1 document.
 */
public final class PostmanCollection {

    private final JsonObject json;

    private PostmanCollection(JsonObject json) {
        this.json = json;
    }

    /**
     * Wraps a parsed collection document.
     * <p>
     * The Postman cloud API returns the collection nested under a {@code collection} property, and some exports do the
     * same, so that envelope is unwrapped here to give callers one shape.
     *
     * @param  root the parsed document
     * @return      the collection
     */
    public static PostmanCollection parse(JsonObject root) {
        JsonObject nested = PostmanJson.asObject(root.get("collection"));
        return new PostmanCollection(nested != null ? nested : root);
    }

    public JsonObject getInfo() {
        return PostmanJson.asObject(json.get("info"));
    }

    public String getName() {
        JsonObject info = getInfo();
        return info != null ? PostmanJson.asString(info.get("name")) : null;
    }

    /**
     * The declared schema URL, for example
     * {@code https://schema.getpostman.com/json/collection/v2.1.0/collection.json}.
     */
    public String getSchema() {
        JsonObject info = getInfo();
        return info != null ? PostmanJson.asString(info.get("schema")) : null;
    }

    /**
     * The collection level variables, with disabled entries removed.
     */
    public Map<String, String> getVariables() {
        return variablesOf(json);
    }

    public PostmanAuth getAuth() {
        return PostmanAuth.parse(json.get("auth"));
    }

    /**
     * The top level items, each of which may be a request or a folder.
     */
    public List<?> getItems() {
        return PostmanJson.asList(json.get("item"));
    }

    public JsonObject getJson() {
        return json;
    }

    /**
     * Reads the {@code variable} array of a collection or folder node into an ordered map, skipping disabled entries.
     */
    public static Map<String, String> variablesOf(JsonObject node) {
        Map<String, String> answer = new LinkedHashMap<>();
        for (PostmanKeyValue variable : PostmanKeyValue.listFrom(node, "variable")) {
            if (!variable.disabled()) {
                answer.put(variable.key(), variable.value() != null ? variable.value() : "");
            }
        }
        return answer;
    }
}
