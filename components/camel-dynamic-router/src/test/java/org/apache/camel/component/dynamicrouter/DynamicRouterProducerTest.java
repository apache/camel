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

import org.apache.camel.component.dynamicrouter.support.CamelDynamicRouterTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.when;

class DynamicRouterProducerTest extends CamelDynamicRouterTestSupport {

    @BeforeEach
    void localSetup() throws Exception {
        super.setup();
        // Remove the interactions defined in the superclass because
        // this test class needs custom behavior
        Mockito.reset(component);
        producer = new DynamicRouterProducer(endpoint);
    }

    @Test
    void testCheckConsumer() throws Exception {
        when(component.getConsumer(DYNAMIC_ROUTER_CHANNEL, true, TIMEOUT))
                .thenReturn(consumer);
        boolean result = producer.checkConsumer(exchange);
        Assertions.assertTrue(result);
    }

    @Test
    void testCheckConsumerNull() throws Exception {
        when(component.getConsumer(DYNAMIC_ROUTER_CHANNEL, true, TIMEOUT))
                .thenReturn(null);
        boolean result = producer.checkConsumer(exchange);
        Assertions.assertFalse(result);
    }

    @Test
    void testCheckConsumerInitiallyNullAndFailIfNoConsumers() {
        when(component.getConsumer(DYNAMIC_ROUTER_CHANNEL))
                .thenReturn(null);
        when(endpoint.isFailIfNoConsumers()).thenReturn(true);
        Assertions.assertThrows(
                DynamicRouterConsumerNotAvailableException.class,
                () -> producer.checkConsumer(exchange));
    }

    @Test
    void testCheckConsumerExceptionIgnored() throws Exception {
        when(component.getConsumer(DYNAMIC_ROUTER_CHANNEL, true, TIMEOUT))
                .thenThrow(new InterruptedException("test"));
        boolean result = producer.checkConsumer(exchange);
        Assertions.assertFalse(result);
    }

    @Test
    void testProcess() throws Exception {
        when(component.getConsumer(DYNAMIC_ROUTER_CHANNEL))
                .thenReturn(consumer);
        when(component.getConsumer(DYNAMIC_ROUTER_CHANNEL, true, TIMEOUT))
                .thenReturn(consumer);
        Assertions.assertDoesNotThrow(() -> producer.process(exchange));
    }

    @Test
    void testProcessSynchronous() throws Exception {
        when(component.getConsumer(DYNAMIC_ROUTER_CHANNEL, true, TIMEOUT))
                .thenReturn(consumer);
        when(endpoint.isSynchronous()).thenReturn(true);
        boolean result = producer.process(exchange, asyncCallback);
        Assertions.assertTrue(result);
    }

    @Test
    void testProcessAynchronous() {
        when(endpoint.isSynchronous()).thenReturn(false);
        boolean result = producer.process(exchange, asyncCallback);
        Assertions.assertTrue(result);
    }
}
