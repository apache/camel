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
 * project, which decides whether the binding writes a Map for the route.
 *
 * @param bodylessEndpoints the {@code direct:} endpoints of the operations of a contract-first binding whose request
 *                          carries no body (CAMEL-24844)
 * @param operations        per specification, keyed as {@link #specName(String)} names it: the HTTP verb of each
 *                          operationId, in lower case
 * @param restBindingMode   the binding mode the project's properties set for REST ({@code off} when they set none), or
 *                          null when not known: then nothing is said that a binding could change
 * @param requestFields     per specification, keyed as {@link #operations()} is: the fields the JSON request of an
 *                          operationId needs, for the operations whose request schema requires some
 */
public record ProjectFacts(Set<String> bodylessEndpoints, Map<String, Map<String, String>> operations,
        String restBindingMode, Map<String, Map<String, List<String>>> requestFields) {

    /** Nothing known about the project. */
    public static final ProjectFacts NONE = new ProjectFacts(Set.of(), Map.of(), null);

    public ProjectFacts {
        bodylessEndpoints = bodylessEndpoints != null ? bodylessEndpoints : Set.of();
        operations = operations != null ? operations : Map.of();
        requestFields = requestFields != null ? requestFields : Map.of();
    }

    /** Without the fields of the requests. */
    public ProjectFacts(Set<String> bodylessEndpoints, Map<String, Map<String, String>> operations,
                        String restBindingMode) {
        this(bodylessEndpoints, operations, restBindingMode, Map.of());
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
