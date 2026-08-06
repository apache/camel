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
import java.util.regex.Pattern;

import org.apache.camel.util.json.JsonObject;

/**
 * Coercion helpers for the union types that pervade the Postman Collection v2.1 format.
 * <p>
 * The schema declares many properties as "one of" several shapes: {@code request} is an object or a string,
 * {@code url.host} is a string or an array of labels, {@code url.path} is a string or an array of segments where each
 * segment is itself a string or an object. Rather than push that ambiguity into every caller, it is resolved once here.
 */
public final class PostmanJson {

    private PostmanJson() {
    }

    /**
     * Coerces a node to a string, tolerating numbers and booleans, which do occur in real collections where the schema
     * says string.
     *
     * @return the string form, or {@code null} when the node is absent or is a container
     */
    public static String asString(Object node) {
        if (node == null) {
            return null;
        }
        if (node instanceof String s) {
            return s;
        }
        if (node instanceof Number || node instanceof Boolean) {
            return node.toString();
        }
        return null;
    }

    /**
     * Returns a node as a {@link JsonObject}, or {@code null} when it is absent or is not an object.
     */
    public static JsonObject asObject(Object node) {
        return node instanceof JsonObject o ? o : null;
    }

    /**
     * Returns a node as a list, or an empty list when it is absent or is not an array.
     */
    public static List<?> asList(Object node) {
        return node instanceof List<?> l ? l : List.of();
    }

    /**
     * Flattens a "string or array of strings" node into a list of strings.
     * <p>
     * Array elements that are objects are read via their {@code value} property, which is how Postman expresses a path
     * segment that carries extra metadata.
     *
     * @param  node      the node, may be {@code null}
     * @param  separator when the node is a plain string, the separator to split it on
     * @return           the parts with empty entries removed, never {@code null}
     */
    public static List<String> asStringList(Object node, String separator) {
        if (node == null) {
            return List.of();
        }
        if (node instanceof String s) {
            return List.of(s.split(Pattern.quote(separator))).stream()
                    .filter(p -> !p.isEmpty())
                    .toList();
        }
        if (node instanceof List<?> list) {
            return list.stream()
                    .map(element -> {
                        if (element instanceof JsonObject o) {
                            return asString(o.get("value"));
                        }
                        return asString(element);
                    })
                    .filter(p -> p != null && !p.isEmpty())
                    .toList();
        }
        return List.of();
    }
}
