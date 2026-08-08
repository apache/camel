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
 * A {@code {key, value, disabled, description}} tuple, which is how the Postman Collection format expresses headers,
 * query parameters and variables.
 */
public record PostmanKeyValue(String key, String value, boolean disabled, String description) {

    /**
     * Reads a list of key/value tuples from a collection node.
     * <p>
     * The value is tolerated as a non-string (numbers appear in the wild) and coerced via {@code toString}. Entries
     * without a key are skipped, as they carry no usable information.
     *
     * @param  node the enclosing node, may be {@code null}
     * @param  key  the property holding the array, for example {@code header} or {@code query}
     * @return      the tuples in document order, never {@code null}
     */
    public static List<PostmanKeyValue> listFrom(JsonObject node, String key) {
        List<PostmanKeyValue> answer = new ArrayList<>();
        if (node == null) {
            return answer;
        }
        Object raw = node.get(key);
        if (!(raw instanceof List<?> list)) {
            return answer;
        }
        for (Object element : list) {
            if (element instanceof JsonObject entry) {
                String name = PostmanJson.asString(entry.get("key"));
                if (name == null || name.isEmpty()) {
                    continue;
                }
                answer.add(new PostmanKeyValue(
                        name,
                        PostmanJson.asString(entry.get("value")),
                        Boolean.TRUE.equals(entry.get("disabled")),
                        PostmanJson.asString(entry.get("description"))));
            }
        }
        return answer;
    }

    /**
     * Whether this entry contributes a value, that is it is enabled and carries a non-empty value.
     */
    public boolean hasValue() {
        return !disabled && value != null && !value.isEmpty();
    }
}
