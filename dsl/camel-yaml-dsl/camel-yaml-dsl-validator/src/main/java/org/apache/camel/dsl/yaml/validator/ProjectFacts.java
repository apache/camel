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
 */
public record ProjectFacts(Set<String> bodylessEndpoints, Map<String, Map<String, String>> operations,
        String restBindingMode, Map<String, Map<String, List<String>>> requestFields, Map<String, Observed> observed) {

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

    public ProjectFacts {
        bodylessEndpoints = bodylessEndpoints != null ? bodylessEndpoints : Set.of();
        operations = operations != null ? operations : Map.of();
        requestFields = requestFields != null ? requestFields : Map.of();
        observed = observed != null ? observed : Map.of();
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
