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
package org.apache.camel.tracing;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.tracing.decorators.AbstractSpanDecorator;

/**
 * This interface represents a decorator specific to the component/endpoint being instrumented.
 */
public interface SpanDecorator {

    /* Prefix for camel component tag */
    String CAMEL_COMPONENT = "camel-";

    SpanDecorator DEFAULT = new AbstractSpanDecorator() {

        @Override
        public String getComponent() {
            return null;
        }

        @Override
        public String getComponentClassName() {
            return null;
        }

    };

    /**
     * This method indicates whether the component associated with the SpanDecorator should result in a new span being
     * created.
     *
     * @return Whether a new span should be created
     */
    boolean newSpan();

    /**
     * The camel component name associated with the decorator.
     *
     * @return The camel component name
     */
    String getComponent();

    /**
     * The camel component FQN classname associated with the decorator.
     *
     * @return The camel component FQN classname
     */
    String getComponentClassName();

    /**
     * This method returns the operation name to use with the Span representing this exchange and endpoint.
     *
     * @param  exchange The exchange
     * @param  endpoint The endpoint
     * @return          The operation name
     */
    String getOperationName(Exchange exchange, Endpoint endpoint);

    /**
     * This method adds appropriate details (tags/logs) to the supplied span based on the pre processing of the
     * exchange.
     *
     * @param span     The span
     * @param exchange The exchange
     * @param endpoint The endpoint
     */
    void pre(SpanAdapter span, Exchange exchange, Endpoint endpoint);

    /**
     * This method adds appropriate details (tags/logs) to the supplied span based on the post processing of the
     * exchange.
     *
     * @param span     The span
     * @param exchange The exchange
     * @param endpoint The endpoint
     */
    void post(SpanAdapter span, Exchange exchange, Endpoint endpoint);

    /**
     * This method returns the 'span.kind' value for use when the component is initiating a communication.
     *
     * @return The kind
     */
    SpanKind getInitiatorSpanKind();

    /**
     * This method returns the 'span.kind' value for use when the component is receiving a communication.
     *
     * @return The kind
     */
    SpanKind getReceiverSpanKind();

    /**
     * This method returns the map to be used for headers extraction when the component is receiving a communication.
     *
     * @param  map      a map containing the objects
     * @param  encoding whether the headers are encoded
     * @return          The extraction map
     */
    ExtractAdapter getExtractAdapter(Map<String, Object> map, boolean encoding);

    /**
     * This method returns the map to be used for headers injection when the component is receiving a communication.
     *
     * @param  map      a map containing the objects
     * @param  encoding whether the headers are encoded
     * @return          The injection map
     */
    InjectAdapter getInjectAdapter(Map<String, Object> map, boolean encoding);

}
