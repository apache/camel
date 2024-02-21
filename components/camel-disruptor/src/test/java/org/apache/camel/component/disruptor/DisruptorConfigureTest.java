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
package org.apache.camel.component.disruptor;

import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.WaitForTaskToComplete;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class DisruptorConfigureTest extends CamelTestSupport {
    @Test
    void testSizeConfigured() throws Exception {
        final DisruptorEndpoint endpoint = resolveMandatoryEndpoint("disruptor:foo?size=2000",
                DisruptorEndpoint.class);
        assertEquals(2048, endpoint.getBufferSize(), "size");
        assertEquals(2048, endpoint.getRemainingCapacity(), "getRemainingCapacity");
    }

    @Test
    void testIllegalSizeZeroConfigured() {
        try {
            resolveMandatoryEndpoint("disruptor:foo?size=0", DisruptorEndpoint.class);
            fail("Should have thrown exception");
        } catch (ResolveEndpointFailedException e) {
            assertEquals(
                    "Failed to resolve endpoint: disruptor://foo?size=0 due to: size found to be 0, must be greater than 0",
                    e.getMessage());
        }
    }

    @Test
    void testSizeThroughBufferSizeComponentProperty() throws Exception {
        final DisruptorComponent disruptor = context.getComponent("disruptor", DisruptorComponent.class);
        disruptor.setBufferSize(2000);
        assertEquals(2000, disruptor.getBufferSize());
        final DisruptorEndpoint endpoint = resolveMandatoryEndpoint("disruptor:foo", DisruptorEndpoint.class);
        assertEquals(2048, endpoint.getBufferSize(), "size");
        assertEquals(2048, endpoint.getRemainingCapacity(), "getRemainingCapacity");
    }

    @Test
    void testMultipleConsumersConfigured() {
        final DisruptorEndpoint endpoint = resolveMandatoryEndpoint("disruptor:foo?multipleConsumers=true",
                DisruptorEndpoint.class);
        assertEquals(true, endpoint.isMultipleConsumers(), "multipleConsumers");
    }

    @Test
    void testDefaultMultipleConsumersComponentProperty() {
        final DisruptorComponent disruptor = context.getComponent("disruptor", DisruptorComponent.class);
        disruptor.setDefaultMultipleConsumers(true);
        assertEquals(true, disruptor.isDefaultMultipleConsumers());
        final DisruptorEndpoint endpoint = resolveMandatoryEndpoint("disruptor:foo", DisruptorEndpoint.class);
        assertEquals(true, endpoint.isMultipleConsumers(), "multipleConsumers");
        assertEquals(true, endpoint.isMultipleConsumersSupported(), "multipleConsumers");
    }

    @Test
    void testProducerTypeConfigured() {
        final DisruptorEndpoint endpoint = resolveMandatoryEndpoint("disruptor:foo?producerType=Single",
                DisruptorEndpoint.class);
        assertEquals(DisruptorProducerType.Single, endpoint.getDisruptor().getProducerType(), "producerType");
    }

    @Test
    void testDefaultProducerTypeComponentProperty() {
        final DisruptorComponent disruptor = context.getComponent("disruptor", DisruptorComponent.class);
        disruptor.setDefaultProducerType(DisruptorProducerType.Single);
        assertEquals(DisruptorProducerType.Single, disruptor.getDefaultProducerType());
        final DisruptorEndpoint endpoint = resolveMandatoryEndpoint("disruptor:foo", DisruptorEndpoint.class);
        assertEquals(DisruptorProducerType.Single, endpoint.getDisruptor().getProducerType(), "producerType");
    }

    @Test
    void testWaitStrategyConfigured() {
        final DisruptorEndpoint endpoint = resolveMandatoryEndpoint("disruptor:foo?waitStrategy=BusySpin",
                DisruptorEndpoint.class);
        assertEquals(DisruptorWaitStrategy.BusySpin,
                endpoint.getDisruptor().getWaitStrategy(), "waitStrategy");
    }

    @Test
    void testDefaultWaitStrategyComponentProperty() {
        final DisruptorComponent disruptor = context.getComponent("disruptor", DisruptorComponent.class);
        disruptor.setDefaultWaitStrategy(DisruptorWaitStrategy.BusySpin);
        assertEquals(DisruptorWaitStrategy.BusySpin, disruptor.getDefaultWaitStrategy());
        final DisruptorEndpoint endpoint = resolveMandatoryEndpoint("disruptor:foo", DisruptorEndpoint.class);
        assertEquals(DisruptorWaitStrategy.BusySpin,
                endpoint.getDisruptor().getWaitStrategy(), "waitStrategy");
    }

    @Test
    void testConcurrentConsumersConfigured() {
        final DisruptorEndpoint endpoint = resolveMandatoryEndpoint("disruptor:foo?concurrentConsumers=5",
                DisruptorEndpoint.class);
        assertEquals(5, endpoint.getConcurrentConsumers(), "concurrentConsumers");
    }

    @Test
    void testDefaultConcurrentConsumersComponentProperty() {
        final DisruptorComponent disruptor = context.getComponent("disruptor", DisruptorComponent.class);
        disruptor.setDefaultConcurrentConsumers(5);
        assertEquals(5, disruptor.getDefaultConcurrentConsumers());
        final DisruptorEndpoint endpoint = resolveMandatoryEndpoint("disruptor:foo", DisruptorEndpoint.class);
        assertEquals(5, endpoint.getConcurrentConsumers(), "concurrentConsumers");
    }

    @Test
    void testWaitForTaskToCompleteConfigured() {
        final DisruptorEndpoint endpoint = resolveMandatoryEndpoint(
                "disruptor:foo?waitForTaskToComplete=Never", DisruptorEndpoint.class);
        assertEquals(WaitForTaskToComplete.Never,
                endpoint.getWaitForTaskToComplete(), "waitForTaskToComplete");
    }

    @Test
    void testDefaults() {
        final DisruptorEndpoint endpoint = resolveMandatoryEndpoint("disruptor:foo", DisruptorEndpoint.class);
        assertEquals(1, endpoint.getConcurrentConsumers(), "concurrentConsumers: wrong default");
        assertEquals(DisruptorComponent.DEFAULT_BUFFER_SIZE,
                endpoint.getBufferSize(), "bufferSize: wrong default");
        assertEquals(30000L, endpoint.getTimeout(), "timeout: wrong default");
        assertEquals(WaitForTaskToComplete.IfReplyExpected,
                endpoint.getWaitForTaskToComplete(), "waitForTaskToComplete: wrong default");
        assertEquals(DisruptorWaitStrategy.Blocking,
                endpoint.getDisruptor().getWaitStrategy(), "DisruptorWaitStrategy: wrong default");
        assertEquals(false, endpoint.isMultipleConsumers(), "multipleConsumers: wrong default");
        assertEquals(false, endpoint.isMultipleConsumersSupported(), "multipleConsumersSupported");
        assertEquals(DisruptorProducerType.Multi, endpoint.getDisruptor().getProducerType(), "producerType");
    }
}
