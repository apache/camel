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
import java.util.Map;
import java.util.Set;

import org.apache.camel.util.json.JsonObject;

/**
 * The {@code auth} block of a collection, folder or request.
 * <p>
 * Postman stores the parameters of each auth type as an array of {@code {key, value, type}} tuples under a property
 * named after the type, for example {@code auth.bearer[0] = {key: "token", value: "..."}}. That array is flattened into
 * a map here.
 */
public final class PostmanAuth {

    public static final String TYPE_NOAUTH = "noauth";
    public static final String TYPE_BASIC = "basic";
    public static final String TYPE_BEARER = "bearer";
    public static final String TYPE_APIKEY = "apikey";

    /**
     * The auth types this component can reproduce as a static header or query parameter.
     */
    public static final Set<String> SUPPORTED_TYPES = Set.of(TYPE_NOAUTH, TYPE_BASIC, TYPE_BEARER, TYPE_APIKEY);

    private final String type;
    private final Map<String, String> parameters;

    private PostmanAuth(String type, Map<String, String> parameters) {
        this.type = type;
        this.parameters = Map.copyOf(parameters);
    }

    /**
     * @param  node the {@code auth} node, may be {@code null}
     * @return      the auth block, or {@code null} when there is none
     */
    public static PostmanAuth parse(Object node) {
        JsonObject auth = PostmanJson.asObject(node);
        if (auth == null) {
            return null;
        }
        String type = PostmanJson.asString(auth.get("type"));
        if (type == null || type.isEmpty()) {
            return null;
        }

        Map<String, String> parameters = new LinkedHashMap<>();
        for (Object element : PostmanJson.asList(auth.get(type))) {
            if (element instanceof JsonObject entry) {
                String key = PostmanJson.asString(entry.get("key"));
                if (key != null) {
                    parameters.put(key, PostmanJson.asString(entry.get("value")));
                }
            }
        }
        return new PostmanAuth(type, parameters);
    }

    public String getType() {
        return type;
    }

    public String getParameter(String key) {
        return parameters.get(key);
    }

    public String getParameterOrDefault(String key, String defaultValue) {
        String value = parameters.get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Whether this component can turn the auth block into a static header or query parameter. The remaining types
     * ({@code awsv4}, {@code digest}, {@code hawk}, {@code edgegrid}, {@code ntlm}, {@code oauth1}, {@code oauth2})
     * need per-request signing or a token exchange, which belongs to the delegate HTTP component.
     */
    public boolean isSupported() {
        return SUPPORTED_TYPES.contains(type);
    }

    /**
     * Whether this block explicitly disables authentication, which is how a request opts out of an inherited block.
     */
    public boolean isNoAuth() {
        return TYPE_NOAUTH.equals(type);
    }
}
