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
package org.apache.camel.dsl.yaml.validator;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What the validator cannot read from a route file, read by the caller from the files beside it (CAMEL-25762): the
 * OpenAPI operations the routes use, whose verb decides whether a call sends the body, and the REST binding mode of the
 * project, which decides whether the binding writes a Map for the route. And from a running integration of the routes:
 * the bodies their endpoints gave (CAMEL-25750).
 *
 * @param bodylessEndpoints the {@code direct:} endpoints of the operations of a contract-first binding whose request
 *                          carries no body (CAMEL-24844)
 * @param operations        per specification, keyed as {@link #specName(String)} names it: the HTTP verb of each
 *                          operationId, in lower case
 * @param restBindingMode   the binding mode the project's properties set for REST ({@code off} when they set none), or
 *                          null when not known: then nothing is said that a binding could change
 * @param requestFields     per specification, keyed as {@link #operations()} is: the fields the JSON request of an
 *                          operationId needs, for the operations whose request schema requires some
 * @param observed          what a running integration of the routes saw at their endpoints (CAMEL-25750): the body a
 *                          consumer gave, keyed as {@link #consumerKey(String)}, and the body a producer returned,
 *                          keyed as {@link #producerKey(String)}: the types the step after it was reached with
 * @param kamelets          the data types of the Kamelets the routes use, by name (CAMEL-25928): the data type
 *                          transformers Camel runs at the start and the end of their route
 * @param jsonArrays        the files a resource: of the routes loads whose JSON is an array, as the resource names them
 *                          ({@code file:orders.json}): unmarshalled, such a file is a List, not a Map
 * @param jsonFields        the fields of the JSON data the routes read: of the file a resource: loads, keyed as the
 *                          resource names it ({@code file:stock.json}), and of the files in the directory a file
 *                          consumer reads, keyed as {@link #directoryKey(String)} names it
 */
public record ProjectFacts(Set<String> bodylessEndpoints, Map<String, Map<String, String>> operations,
        String restBindingMode, Map<String, Map<String, List<String>>> requestFields, Map<String, Observed> observed,
        Map<String, KameletDataTypes> kamelets, Set<String> jsonArrays, Map<String, JsonFields> jsonFields) {

    /** Nothing known about the project. */
    public static final ProjectFacts NONE = new ProjectFacts(Set.of(), Map.of(), null);

    /**
     * The bodies an endpoint gave in a running integration: their types, and their kinds (null, payload, data or
     * collection, as the {@code BodyTypeRegistry} of Camel names them).
     */
    public record Observed(List<String> types, List<String> kinds) {

        public Observed {
            types = types != null ? List.copyOf(types) : List.of();
            kinds = kinds != null ? List.copyOf(kinds) : List.of();
        }
    }

    /**
     * The data types of a Kamelet: the data type transformer of its default in and out data type (spec.dataTypes), as
     * application-octet-stream or aws2-s3:application-cloudevents; null for a direction it has none for.
     */
    public record KameletDataTypes(String in, String out) {
    }

    /**
     * The fields of JSON data, as the files of the project have them: the keys of its objects (of the elements, for a
     * list), and of each field that holds a list of objects, the keys of those objects. Where says in words where the
     * data is ("the files in orders/"); outer has the fields of the data an element was split from, and is empty
     * otherwise.
     */
    public record JsonFields(String where, Set<String> fields, Map<String, Set<String>> elementFields, boolean list,
            Set<String> outer) {

        public JsonFields {
            fields = fields != null ? Set.copyOf(fields) : Set.of();
            elementFields = elementFields != null
                    ? elementFields.entrySet().stream()
                            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> Set.copyOf(e.getValue())))
                    : Map.of();
            outer = outer != null ? Set.copyOf(outer) : Set.of();
        }
    }

    /** The key of the JSON data in the directory a file consumer reads: {@code dir:orders}. */
    public static String directoryKey(String directory) {
        String d = directory.trim();
        while (d.startsWith("./")) {
            d = d.substring(2);
        }
        while (d.endsWith("/") && d.length() > 1) {
            d = d.substring(0, d.length() - 1);
        }
        return "dir:" + d;
    }

    public ProjectFacts {
        bodylessEndpoints = bodylessEndpoints != null ? bodylessEndpoints : Set.of();
        operations = operations != null ? operations : Map.of();
        requestFields = requestFields != null ? requestFields : Map.of();
        observed = observed != null ? observed : Map.of();
        kamelets = kamelets != null ? kamelets : Map.of();
        jsonArrays = jsonArrays != null ? jsonArrays : Set.of();
        jsonFields = jsonFields != null ? jsonFields : Map.of();
    }

    /** Without the fields of the JSON data of the project. */
    public ProjectFacts(Set<String> bodylessEndpoints, Map<String, Map<String, String>> operations,
                        String restBindingMode, Map<String, Map<String, List<String>>> requestFields,
                        Map<String, Observed> observed, Map<String, KameletDataTypes> kamelets, Set<String> jsonArrays) {
        this(bodylessEndpoints, operations, restBindingMode, requestFields, observed, kamelets, jsonArrays, Map.of());
    }

    /** Without the JSON arrays of the project. */
    public ProjectFacts(Set<String> bodylessEndpoints, Map<String, Map<String, String>> operations,
                        String restBindingMode, Map<String, Map<String, List<String>>> requestFields,
                        Map<String, Observed> observed, Map<String, KameletDataTypes> kamelets) {
        this(bodylessEndpoints, operations, restBindingMode, requestFields, observed, kamelets, Set.of());
    }

    /** Without the data types of the Kamelets. */
    public ProjectFacts(Set<String> bodylessEndpoints, Map<String, Map<String, String>> operations,
                        String restBindingMode, Map<String, Map<String, List<String>>> requestFields,
                        Map<String, Observed> observed) {
        this(bodylessEndpoints, operations, restBindingMode, requestFields, observed, Map.of());
    }

    /** Without what a running integration saw. */
    public ProjectFacts(Set<String> bodylessEndpoints, Map<String, Map<String, String>> operations,
                        String restBindingMode, Map<String, Map<String, List<String>>> requestFields) {
        this(bodylessEndpoints, operations, restBindingMode, requestFields, Map.of());
    }

    /** Without the fields of the requests. */
    public ProjectFacts(Set<String> bodylessEndpoints, Map<String, Map<String, String>> operations,
                        String restBindingMode) {
        this(bodylessEndpoints, operations, restBindingMode, Map.of());
    }

    /** The key of the body a consumer gave: {@code from:} and the endpoint without its query. */
    public static String consumerKey(String uri) {
        return "from:" + RouteGraph.normalize(uri);
    }

    /** The key of the body a producer returned: {@code to:} and the endpoint without its query. */
    public static String producerKey(String uri) {
        return "to:" + RouteGraph.normalize(uri);
    }

    /**
     * A specification as the routes name it, as {@link #operations()} keys it: the file without {@code file:} or
     * {@code classpath:}, and {@code openapi.json}, the default of rest-openapi, when none is named.
     */
    public static String specName(String specification) {
        String file = specification == null || specification.isBlank() ? "openapi.json" : specification.trim();
        for (String prefix : new String[] { "file:", "classpath:" }) {
            if (file.startsWith(prefix)) {
                file = file.substring(prefix.length());
            }
        }
        return file;
    }
}
