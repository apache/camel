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

import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HazelcastRingbufferProducerTest extends HazelcastCamelTestSupport {

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
    
    @Test
    public void testReadHead() throws InterruptedException {
        when(ringbuffer.readOne(Matchers.anyLong())).thenReturn("pippo");
        Object result = template.requestBody("direct:readonceHead", 12L, String.class);
        assertEquals("pippo", result);
    }
    
    @Test
    public void testReadTail() throws InterruptedException {
        when(ringbuffer.readOne(Matchers.anyLong())).thenReturn("pippo");
        Object result = template.requestBody("direct:readonceTail", 12L, String.class);
        assertEquals("pippo", result);
    }
    
    @Test
    public void testAdd() throws InterruptedException {
        when(ringbuffer.add(Matchers.anyLong())).thenReturn(13L);
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

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
              
                from("direct:readonceHead").setHeader(HazelcastConstants.OPERATION, constant(HazelcastConstants.READ_ONCE_HEAD_OPERATION)).to(
                        String.format("hazelcast:%sfoo", HazelcastConstants.RINGBUFFER_PREFIX));
                
                from("direct:readonceTail").setHeader(HazelcastConstants.OPERATION, constant(HazelcastConstants.READ_ONCE_TAIL_OPERATION)).to(
                        String.format("hazelcast:%sfoo", HazelcastConstants.RINGBUFFER_PREFIX));
                
                from("direct:add").setHeader(HazelcastConstants.OPERATION, constant(HazelcastConstants.ADD_OPERATION)).to(
                        String.format("hazelcast:%sfoo", HazelcastConstants.RINGBUFFER_PREFIX));
                
                from("direct:capacity").setHeader(HazelcastConstants.OPERATION, constant(HazelcastConstants.GET_CAPACITY_OPERATION)).to(
                        String.format("hazelcast:%sfoo", HazelcastConstants.RINGBUFFER_PREFIX));
                
                from("direct:remainingCapacity").setHeader(HazelcastConstants.OPERATION, constant(HazelcastConstants.REMAINING_CAPACITY_OPERATION)).to(
                        String.format("hazelcast:%sfoo", HazelcastConstants.RINGBUFFER_PREFIX));

            }
        };
    }

}
