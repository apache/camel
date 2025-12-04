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

import java.util.Map;
import java.util.Set;

import org.apache.camel.Exchange;

/**
 * Used for validating incoming client requests with Camel Rest DSL.
 * <p>
 * This allows to plugin different validators.
 */
public interface RestClientRequestValidator {

    String FACTORY = "rest-client-request-validator-factory";

    /**
     * Validation error
     *
     * @param statusCode to use a specific HTTP status code for this validation error
     * @param body       to use a specific message body for this validation error
     */
    record ValidationError(int statusCode, String body) {}

    /**
     * Validation context to use during validation
     *
     * @param consumes                content-type this service can consume
     * @param produces                content-type this service can produce
     * @param requiredBody            whether the message body is required
     * @param queryDefaultValues      default values for HTTP query parameters
     * @param queryAllowedValues      allowed values for HTTP query parameters
     * @param requiredQueryParameters names of HTTP query parameters that are required
     * @param requiredHeaders         names of HTTP headers parameters that are required
     */
    record ValidationContext(
            String consumes,
            String produces,
            boolean requiredBody,
            Map<String, String> queryDefaultValues,
            Map<String, String> queryAllowedValues,
            Set<String> requiredQueryParameters,
            Set<String> requiredHeaders) {}

    /**
     * Validates the incoming client request
     *
     * @param  exchange          the current exchange
     * @param  validationContent validation context
     * @return                   the validation error, or <tt>null</tt> if success
     */
    ValidationError validate(Exchange exchange, ValidationContext validationContent);
}
