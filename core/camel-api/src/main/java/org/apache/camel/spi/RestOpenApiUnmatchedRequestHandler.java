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
package org.apache.camel.spi;

import java.util.List;

import org.apache.camel.Exchange;

/**
 * Used for customizing the HTTP responses for incoming requests that Camel must not process. Exemplary these are
 * requests that do not match any operation defined in the OpenAPI specification (404/405) or, when server request
 * validation is enabled, requests whose Content-Type/Accept header does not match the consumes/produces of the matched
 * operation (415/406).
 * <p>
 * This allows to plugin different handlers to produce custom error response bodies.
 *
 * @since 4.23
 */
public interface RestOpenApiUnmatchedRequestHandler {

    String FACTORY = "rest-openapi-unmatched-request-handler-factory";

    /**
     * Handles the incoming request that is not processed by Camel.
     *
     * @param exchange       the current exchange
     * @param statusCode     the HTTP status code (404, 405, 415 or 406)
     * @param allowedMethods the list of allowed HTTP methods for the requested path (empty for 404, 415 and 406)
     */
    void handle(Exchange exchange, int statusCode, List<String> allowedMethods);
}
