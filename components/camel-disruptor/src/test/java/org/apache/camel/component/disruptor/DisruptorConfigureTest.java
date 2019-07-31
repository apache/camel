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
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class DisruptorConfigureTest extends CamelTestSupport {
    @Test
    public void testSizeConfigured() throws Exception {
        final DisruptorEndpoint endpoint = resolveMandatoryEndpoint("disruptor:foo?size=2000",
                DisruptorEndpoint.class);
        assertEquals("size", 2048, endpoint.getBufferSize());
        assertEquals("getRemainingCapacity", 2048, endpoint.getRemainingCapacity());
    }

    @Test
    public void testIllegalSizeZeroConfigured() throws Exception {
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
    public void testSizeThroughBufferSizeComponentProperty() throws Exception {
        final DisruptorComponent disruptor = context.getComponent("disruptor", DisruptorComponent.class);
        disruptor.setBufferSize(2000);
        assertEquals(2000, disruptor.getBufferSize());
        final DisruptorEndpoint endpoint = resolveMandatoryEndpoint("disruptor:foo", DisruptorEndpoint.class);
        assertEquals("size", 2048, endpoint.getBufferSize());
        assertEquals("getRemainingCapacity", 2048, endpoint.getRemainingCapacity());
    }

    @Test
    public void testMultipleConsumersConfigured() {
        final DisruptorEndpoint endpoint = resolveMandatoryEndpoint("disruptor:foo?multipleConsumers=true",
                DisruptorEndpoint.class);
        assertEquals("multipleConsumers", true, endpoint.isMultipleConsumers());
    }

    @Test
    public void testDefaultMultipleConsumersComponentProperty() throws Exception {
        final DisruptorComponent disruptor = context.getComponent("disruptor", DisruptorComponent.class);
        disruptor.setDefaultMultipleConsumers(true);
        assertEquals(true, disruptor.isDefaultMultipleConsumers());
        final DisruptorEndpoint endpoint = resolveMandatoryEndpoint("disruptor:foo", DisruptorEndpoint.class);
        assertEquals("multipleConsumers", true, endpoint.isMultipleConsumers());
        assertEquals("multipleConsumers", true, endpoint.isMultipleConsumersSupported());
    }

    @Test
    public void testProducerTypeConfigured() {
        final DisruptorEndpoint endpoint = resolveMandatoryEndpoint("disruptor:foo?producerType=Single",
                DisruptorEndpoint.class);
        assertEquals("producerType", DisruptorProducerType.Single, endpoint.getDisruptor().getProducerType());
    }

    @Test
    public void testDefaultProducerTypeComponentProperty() throws Exception {
        final DisruptorComponent disruptor = context.getComponent("disruptor", DisruptorComponent.class);
        disruptor.setDefaultProducerType(DisruptorProducerType.Single);
        assertEquals(DisruptorProducerType.Single, disruptor.getDefaultProducerType());
        final DisruptorEndpoint endpoint = resolveMandatoryEndpoint("disruptor:foo", DisruptorEndpoint.class);
        assertEquals("producerType", DisruptorProducerType.Single, endpoint.getDisruptor().getProducerType());
    }

    @Test
    public void testWaitStrategyConfigured() {
        final DisruptorEndpoint endpoint = resolveMandatoryEndpoint("disruptor:foo?waitStrategy=BusySpin",
                DisruptorEndpoint.class);
        assertEquals("waitStrategy", DisruptorWaitStrategy.BusySpin,
                endpoint.getDisruptor().getWaitStrategy());
    }

    @Test
    public void testDefaultWaitStrategyComponentProperty() throws Exception {
        final DisruptorComponent disruptor = context.getComponent("disruptor", DisruptorComponent.class);
        disruptor.setDefaultWaitStrategy(DisruptorWaitStrategy.BusySpin);
        assertEquals(DisruptorWaitStrategy.BusySpin, disruptor.getDefaultWaitStrategy());
        final DisruptorEndpoint endpoint = resolveMandatoryEndpoint("disruptor:foo", DisruptorEndpoint.class);
        assertEquals("waitStrategy", DisruptorWaitStrategy.BusySpin,
                endpoint.getDisruptor().getWaitStrategy());
    }

    @Test
    public void testConcurrentConsumersConfigured() {
        final DisruptorEndpoint endpoint = resolveMandatoryEndpoint("disruptor:foo?concurrentConsumers=5",
                DisruptorEndpoint.class);
        assertEquals("concurrentConsumers", 5, endpoint.getConcurrentConsumers());
    }

    @Test
    public void testDefaultConcurrentConsumersComponentProperty() throws Exception {
        final DisruptorComponent disruptor = context.getComponent("disruptor", DisruptorComponent.class);
        disruptor.setDefaultConcurrentConsumers(5);
        assertEquals(5, disruptor.getDefaultConcurrentConsumers());
        final DisruptorEndpoint endpoint = resolveMandatoryEndpoint("disruptor:foo", DisruptorEndpoint.class);
        assertEquals("concurrentConsumers", 5, endpoint.getConcurrentConsumers());
    }

    @Test
    public void testWaitForTaskToCompleteConfigured() {
        final DisruptorEndpoint endpoint = resolveMandatoryEndpoint(
                "disruptor:foo?waitForTaskToComplete=Never", DisruptorEndpoint.class);
        assertEquals("waitForTaskToComplete", WaitForTaskToComplete.Never,
                endpoint.getWaitForTaskToComplete());
    }

    @Test
    public void testDefaults() {
        final DisruptorEndpoint endpoint = resolveMandatoryEndpoint("disruptor:foo", DisruptorEndpoint.class);
        assertEquals("concurrentConsumers: wrong default", 1, endpoint.getConcurrentConsumers());
        assertEquals("bufferSize: wrong default", DisruptorComponent.DEFAULT_BUFFER_SIZE,
                endpoint.getBufferSize());
        assertEquals("timeout: wrong default", 30000L, endpoint.getTimeout());
        assertEquals("waitForTaskToComplete: wrong default", WaitForTaskToComplete.IfReplyExpected,
                endpoint.getWaitForTaskToComplete());
        assertEquals("DisruptorWaitStrategy: wrong default", DisruptorWaitStrategy.Blocking,
                endpoint.getDisruptor().getWaitStrategy());
        assertEquals("multipleConsumers: wrong default", false, endpoint.isMultipleConsumers());
        assertEquals("multipleConsumersSupported", false, endpoint.isMultipleConsumersSupported());
        assertEquals("producerType", DisruptorProducerType.Multi, endpoint.getDisruptor().getProducerType());
    }
}
