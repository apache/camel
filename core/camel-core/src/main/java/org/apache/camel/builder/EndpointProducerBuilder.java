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
package org.apache.camel.builder;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Expression;
import org.apache.camel.NoSuchEndpointException;

/**
 * Type-safe endpoint DSL for building producer endpoints.
 *
 * @see EndpointConsumerBuilder
 */
public interface EndpointProducerBuilder {

    /**
     * Builds and resolves this endpoint DSL as an endpoint.
     *
     * @param context  the camel context
     * @return a built {@link Endpoint}
     * @throws NoSuchEndpointException is thrown if the endpoint
     */
    Endpoint resolve(CamelContext context) throws NoSuchEndpointException;

    /**
     * Builds the url of this endpoint.
     * This API is only intended for Camel internally.
     */
    String getUri();

    /**
     * Adds an option to this endpoint.
     * This API is only intended for Camel internally.
     */
    void setProperty(String name, Object value);

    /**
     * Builds an expression of this endpoint url.
     * This API is only intended for Camel internally.
     */
    Expression expr();

}
