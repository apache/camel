/**
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
package org.apache.camel.opentracing;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.opentracing.decorators.AbstractSpanDecorator;

import io.opentracing.Span;

/**
 * This interface represents a decorator specific to the component/endpoint
 * being instrumented.
 *
 */
public interface SpanDecorator {

    /* Prefix for camel component tag */
    public static final String CAMEL_COMPONENT = "camel-";

    /**
     * The camel component associated with the decorator.
     *
     * @return The camel component name
     */
    String getComponent();

    /**
     * This method returns the operation name to use with the Span representing
     * this exchange and endpoint.
     *
     * @param exchange The exchange
     * @param endpoint The endpoint
     * @return The operation name
     */
    String getOperationName(Exchange exchange, Endpoint endpoint);

    /**
     * This method adds appropriate details (tags/logs) to the supplied span
     * based on the pre processing of the exchange.
     *
     * @param span The span
     * @param exchange The exchange
     * @param endpoint The endpoint
     */
    void pre(Span span, Exchange exchange, Endpoint endpoint);

    /**
     * This method adds appropriate details (tags/logs) to the supplied span
     * based on the post processing of the exchange.
     *
     * @param span The span
     * @param exchange The exchange
     * @param endpoint The endpoint
     */
    void post(Span span, Exchange exchange, Endpoint endpoint);

    SpanDecorator DEFAULT = new AbstractSpanDecorator() {

        @Override
        public String getComponent() {
            return null;
        }
        
    };

}
