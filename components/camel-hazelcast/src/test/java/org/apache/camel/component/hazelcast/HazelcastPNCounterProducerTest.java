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
package org.apache.camel.component.hazelcast;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.crdt.pncounter.PNCounter;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class HazelcastPNCounterProducerTest extends HazelcastCamelTestSupport {

    @Mock
    private PNCounter pnCounter;

    @Override
    protected void trainHazelcastInstance(HazelcastInstance hazelcastInstance) {
        when(hazelcastInstance.getPNCounter("foo")).thenReturn(pnCounter);
    }

    @Override
    protected void verifyHazelcastInstance(HazelcastInstance hazelcastInstance) {
        verify(hazelcastInstance, times(6)).getPNCounter("foo");
    }

    @AfterEach
    public void verifyPNCounterMock() {
        verifyNoMoreInteractions(pnCounter);
    }

    @Test
    public void testWithInvalidOperationName() {
        assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:setInvalid", 4711));
    }

    @Test
    public void testGet() {
        when(pnCounter.get()).thenReturn(1234L);
        long body = template.requestBody("direct:get", null, Long.class);
        verify(pnCounter).get();
        assertEquals(1234, body);
    }

    @Test
    public void testIncrement() {
        when(pnCounter.incrementAndGet()).thenReturn(11L);
        long body = template.requestBody("direct:increment", null, Long.class);
        verify(pnCounter).incrementAndGet();
        assertEquals(11, body);
    }

    @Test
    public void testDecrement() {
        when(pnCounter.decrementAndGet()).thenReturn(9L);
        long body = template.requestBody("direct:decrement", null, Long.class);
        verify(pnCounter).decrementAndGet();
        assertEquals(9, body);
    }

    @Test
    public void testGetAndAdd() {
        when(pnCounter.getAndAdd(12L)).thenReturn(13L);
        long result = template.requestBody("direct:getAndAdd", 12L, Long.class);
        verify(pnCounter).getAndAdd(12L);
        assertEquals(13L, result);
    }

    @Test
    public void testDestroy() throws InterruptedException {
        template.sendBody("direct:destroy", null);
        verify(pnCounter).destroy();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("direct:setInvalid").setHeader(HazelcastConstants.OPERATION, constant("invalid"))
                        .to(String.format("hazelcast-%sfoo", HazelcastConstants.PNCOUNTER_PREFIX));

                from("direct:get").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.GET))
                        .to(String.format("hazelcast-%sfoo", HazelcastConstants.PNCOUNTER_PREFIX));

                from("direct:increment").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.INCREMENT)).to(
                        String.format("hazelcast-%sfoo", HazelcastConstants.PNCOUNTER_PREFIX));

                from("direct:decrement").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.DECREMENT)).to(
                        String.format("hazelcast-%sfoo", HazelcastConstants.PNCOUNTER_PREFIX));

                from("direct:getAndAdd").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.GET_AND_ADD)).to(
                        String.format("hazelcast-%sfoo", HazelcastConstants.PNCOUNTER_PREFIX));

                from("direct:destroy").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.DESTROY)).to(
                        String.format("hazelcast-%sfoo", HazelcastConstants.PNCOUNTER_PREFIX));

            }
        };
    }

}
