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

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointProducerResolver;
import org.apache.camel.Expression;

/**
 * Type-safe endpoint DSL for building producer endpoints.
 *
 * @see EndpointConsumerBuilder
 */
public interface EndpointProducerBuilder extends EndpointProducerResolver {
    /**
     * Builds the url of this endpoint. This API is only intended for Camel internally.
     */
    String getUri();

    /**
     * Adds an option to this endpoint. This API is only intended for Camel internally.
     */
    void doSetProperty(String name, Object value);

    /**
     * Adds a multi-value option to this endpoint. This API is only intended for Camel internally.
     */
    void doSetMultiValueProperty(String name, String key, Object value);

    /**
     * Adds multi-value options to this endpoint. This API is only intended for Camel internally.
     */
    void doSetMultiValueProperties(String name, String prefix, Map<String, Object> values);

    /**
     * Builds an expression of this endpoint url. This API is only intended for Camel internally.
     */
    Expression expr();

    /**
     * Builds a dynamic expression of this endpoint url. This API is only intended for Camel internally.
     */
    Expression expr(CamelContext camelContext);

}
