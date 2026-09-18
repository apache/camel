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

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * Removes credentials from a collection document before it is served over HTTP.
 * <p>
 * Serving the collection publishes route-author configuration to whoever can reach the API context path, and a real
 * collection routinely carries live tokens in its {@code auth} blocks and in variables marked as secret. Redaction is
 * therefore unconditional rather than an option.
 */
public final class PostmanRedactor {

    private static final String REDACTED = "***";

    private PostmanRedactor() {
    }

    /**
     * Returns a deep copy of the document with every {@code auth} block removed and the value of every variable of type
     * {@code secret} replaced.
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
            return answer;
        }
        if (node instanceof List<?> list) {
            List<Object> answer = new ArrayList<>(list.size());
            for (Object element : list) {
                Object redacted = redactNode(element, null);
                if ("variable".equals(key) && redacted instanceof JsonObject variable) {
                    redactSecretVariable(variable);
                }
                answer.add(redacted);
            }
            return new JsonArray(answer);
        }
        return node;
    }

    private static void redactSecretVariable(JsonObject variable) {
        Object type = variable.get("type");
        if ("secret".equals(type) && variable.containsKey("value")) {
            variable.put("value", REDACTED);
        }
    }
}
