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

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.SimpleRequest;
import org.apache.camel.Exchange;

/**
 * An abstraction for customizing the behavior of OpenApi request validation.
 */
public interface RequestValidationCustomizer {
    /**
     * Customizes the creation of a {@link OpenApiInteractionValidator}. The default implementation enables validation
     * of only the request body.
     *
     * @param builder The {@link OpenApiInteractionValidator} builder to be customized
     */
    default void customizeOpenApiInteractionValidator(OpenApiInteractionValidator.Builder builder) {
        // Noop
    }

    /**
     * Applies customizations the creation of a {@link SimpleRequest} to be validated by
     * {@link OpenApiInteractionValidator}.
     *
     * @param builder   The {@link SimpleRequest} builder to be customized
     * @param operation The {@link RestOpenApiOperation} containing details of the API operation associated with the
     *                  request
     * @param exchange  The message exchange being processed
     */
    default void customizeSimpleRequestBuilder(
            SimpleRequest.Builder builder, RestOpenApiOperation operation, Exchange exchange) {
        // Noop
    }
}
