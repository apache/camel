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
import org.apache.camel.component.dynamicrouter.support.CamelDynamicRouterTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DynamicRouterComponentTest extends CamelDynamicRouterTestSupport {

    @BeforeEach
    void localSetup() throws Exception {
        super.setup();
        component = new DynamicRouterComponent(
                () -> endpointFactory, () -> processorFactory, () -> controlChannelProcessorFactory, () -> producerFactory,
                () -> consumerFactory, () -> filterProcessorFactory);
    }

    @Test
    void createEndpoint() throws Exception {
        component.setCamelContext(context);
        Endpoint actualEndpoint = component.createEndpoint("testname", "remaining", Collections.emptyMap());
        assertEquals(endpoint, actualEndpoint);
    }

    @Test
    void addConsumer() {
        component.addConsumer(DYNAMIC_ROUTER_CHANNEL, consumer);
        assertEquals(consumer, component.getConsumer(DYNAMIC_ROUTER_CHANNEL));
    }

    @Test
    void getConsumer() {
        addConsumer();
        final DynamicRouterConsumer result = component.getConsumer(DYNAMIC_ROUTER_CHANNEL);
        Assertions.assertEquals(consumer, result);
    }

    @Test
    void getConsumerBlock() throws InterruptedException {
        addConsumer();
        final DynamicRouterConsumer result = component.getConsumer(DYNAMIC_ROUTER_CHANNEL, true, endpoint.getTimeout());
        Assertions.assertEquals(consumer, result);
    }

    @Test
    void addDuplicateConsumer() {
        component.addConsumer(DYNAMIC_ROUTER_CHANNEL, consumer);
        assertEquals(consumer, component.getConsumer(DYNAMIC_ROUTER_CHANNEL));
        assertThrows(IllegalArgumentException.class, () -> component.addConsumer(DYNAMIC_ROUTER_CHANNEL, consumer));
    }

    @Test
    void removeConsumer() {
        component.addConsumer(DYNAMIC_ROUTER_CHANNEL, consumer);
        assertEquals(consumer, component.getConsumer(DYNAMIC_ROUTER_CHANNEL));
        component.removeConsumer(DYNAMIC_ROUTER_CHANNEL, consumer);
        assertNull(component.getConsumer(DYNAMIC_ROUTER_CHANNEL));
    }
}
