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
package org.apache.camel.component.aws.xray;

import com.amazonaws.xray.entities.Entity;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.aws.xray.decorators.AbstractSegmentDecorator;

/**
 * This interface represents a decorator specific to the component/endpoint being instrumented.
 */
public interface SegmentDecorator {

    /* Prefix for camel component tag */
    String CAMEL_COMPONENT = "camel-";

    SegmentDecorator DEFAULT = new AbstractSegmentDecorator() {

        @Override
        public String getComponent() {
            return null;
        }
    };

    /**
     * This method indicates whether the component associated with the SegmentDecorator should result in a new segment
     * being created.
     *
     * @return Whether a new segment should be created
     */
    boolean newSegment();

    /**
     * The camel component associated with the decorator.
     *
     * @return The camel component name
     */
    String getComponent();

    /**
     * This method returns the operation name to use with the segment representing this exchange and endpoint.
     *
     * @param exchange The exchange
     * @param endpoint The endpoint
     * @return The operation name
     */
    String getOperationName(Exchange exchange, Endpoint endpoint);

    /**
     * This method adds appropriate details (tags/logs) to the supplied segment based on the pre processing of the
     * exchange.
     *
     * @param segment The segment
     * @param exchange The exchange
     * @param endpoint The endpoint
     */
    void pre(Entity segment, Exchange exchange, Endpoint endpoint);

    /**
     * This method adds appropriate details (tags/logs) to the supplied segment based on the post processing of the
     * exchange.
     *
     * @param segment The segment
     * @param exchange The exchange
     * @param endpoint The endpoint
     */
    void post(Entity segment, Exchange exchange, Endpoint endpoint);
}
