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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.context.support.AbstractApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class HazelcastPNCounterProducerForSpringTest extends HazelcastCamelSpringTestSupport {

    @Mock
    private PNCounter pnCounter;

    @Override
    protected void trainHazelcastInstance(HazelcastInstance hazelcastInstance) {
        when(hazelcastInstance.getPNCounter("foo")).thenReturn(pnCounter);
    }

    @Override
    protected void verifyHazelcastInstance(HazelcastInstance hazelcastInstance) {
        verify(hazelcastInstance, times(5)).getPNCounter("foo");
    }

    @AfterEach
    public void verifyPNCounterMock() {
        verifyNoMoreInteractions(pnCounter);
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return newAppContext("/META-INF/spring/test-camel-context-pncounter.xml");
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
}
