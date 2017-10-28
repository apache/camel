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
package org.apache.camel.component.hazelcast;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.ringbuffer.Ringbuffer;

import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HazelcastRingbufferProducerForSpringTest extends HazelcastCamelSpringTestSupport {

    @Mock
    private Ringbuffer<Object> ringbuffer;

    @Override
    protected void trainHazelcastInstance(HazelcastInstance hazelcastInstance) {
        when(hazelcastInstance.getRingbuffer("foo")).thenReturn(ringbuffer);
    }

    @Override
    protected void verifyHazelcastInstance(HazelcastInstance hazelcastInstance) {
        verify(hazelcastInstance, atLeastOnce()).getRingbuffer("foo");
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("/META-INF/spring/test-camel-context-ringbuffer.xml");
    }

    @Test
    public void testReadTail() throws InterruptedException {
        when(ringbuffer.readOne(ArgumentMatchers.anyLong())).thenReturn("pippo");
        Object result = template.requestBody("direct:readonceTail", 12L, String.class);
        assertEquals("pippo", result);
    }
    
    @Test
    public void testAdd() throws InterruptedException {
        when(ringbuffer.add(ArgumentMatchers.anyLong())).thenReturn(13L);
        Object result = template.requestBody("direct:add", 12L, Long.class);
        assertEquals(13L, result);
    }
    
    @Test
    public void testCapacity() throws InterruptedException {
        when(ringbuffer.capacity()).thenReturn(13L);
        Object result = template.requestBody("direct:capacity", 12L, Long.class);
        assertEquals(13L, result);
    }
    
    @Test
    public void testRemainingCapacity() throws InterruptedException {
        when(ringbuffer.remainingCapacity()).thenReturn(2L);
        Object result = template.requestBody("direct:remainingCapacity", "", Long.class);
        assertEquals(2L, result);
    }
}
