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

package org.apache.camel.component.rest.openapi.validator;

import java.util.Set;

import org.apache.camel.Exchange;

public interface RequestValidator {

    /**
     * Sets the default operation.
     */
    void setOperation(RestOpenApiOperation operation);

    /**
     * Gets the default operation.
     */
    RestOpenApiOperation getOperation();

    /**
     * Validates the {@link Exchange} with the custom operation.
     *
     * @param  exchange  the exchange
     * @param  operation operation to use
     * @return           null if no validation error, otherwise a set of errors
     */
    Set<String> validate(Exchange exchange, RestOpenApiOperation operation);

    /**
     * Validates the {@link Exchange} with the default operation
     *
     * @param  exchange the exchange
     * @return          null if no validation error, otherwise a set of errors
     */
    default Set<String> validate(Exchange exchange) {
        return validate(exchange, getOperation());
    }
}
