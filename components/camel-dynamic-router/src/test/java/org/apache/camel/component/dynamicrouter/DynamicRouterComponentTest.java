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
package org.apache.camel.component.dynamicrouter;

import java.util.Collections;

import org.apache.camel.Endpoint;
import org.apache.camel.component.dynamicrouter.support.DynamicRouterTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DynamicRouterComponentTest extends DynamicRouterTestSupport {

    @BeforeEach
    void localSetup() throws Exception {
        super.setup();
        component = new DynamicRouterComponent(
                () -> endpointFactory, () -> processorFactory, () -> controlChannelProcessorFactory, () -> producerFactory,
                () -> controlProducerFactory, () -> filterProcessorFactory);
    }

    @Test
    void testCreateEndpoint() throws Exception {
        component.setCamelContext(context);
        Endpoint actualEndpoint = component.createEndpoint("dynamic-router:testname", "remaining", Collections.emptyMap());
        assertEquals(endpoint, actualEndpoint);
    }

    @Test
    void testCreateEndpointWithEmptyRemainingError() {
        component.setCamelContext(context);
        assertThrows(IllegalArgumentException.class,
                () -> component.createEndpoint("dynamic-router:testname", "", Collections.emptyMap()));
    }

    @Test
    void testAddRoutingProcessor() {
        component.addRoutingProcessor(DYNAMIC_ROUTER_CHANNEL, processor);
        assertEquals(processor, component.getRoutingProcessor(DYNAMIC_ROUTER_CHANNEL));
    }

    @Test
    void testAddRoutingProcessorWithSecondProcessorForChannelError() {
        component.addRoutingProcessor(DYNAMIC_ROUTER_CHANNEL, processor);
        assertEquals(processor, component.getRoutingProcessor(DYNAMIC_ROUTER_CHANNEL));
        assertThrows(IllegalArgumentException.class, () -> component.addRoutingProcessor(DYNAMIC_ROUTER_CHANNEL, processor));

    }
}
