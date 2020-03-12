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

import java.util.HashMap;
import java.util.Map;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.CPSubsystem;
import com.hazelcast.cp.IAtomicLong;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.After;
import org.junit.Test;
import org.mockito.Mock;

import static org.mockito.Mockito.*;

public class HazelcastAtomicnumberProducerTest extends HazelcastCamelTestSupport {

    @Mock
    private IAtomicLong atomicNumber;

    @Mock
    private CPSubsystem cpSubsystem;

    @Override
    protected void trainHazelcastInstance(HazelcastInstance hazelcastInstance) {
        when(hazelcastInstance.getCPSubsystem()).thenReturn(cpSubsystem);
        when(cpSubsystem.getAtomicLong("foo")).thenReturn(atomicNumber);
    }

    @Override
    protected void verifyHazelcastInstance(HazelcastInstance hazelcastInstance) {
        verify(hazelcastInstance, times(10)).getCPSubsystem();
        verify(cpSubsystem, atLeastOnce()).getAtomicLong("foo");
    }

    @After
    public void verifyAtomicNumberMock() {
        verifyNoMoreInteractions(atomicNumber);
    }

    @Test(expected = CamelExecutionException.class)
    public void testWithInvalidOperationName() {
        template.sendBody("direct:setInvalid", 4711);
    }

    @Test
    public void testSet() {
        template.sendBody("direct:set", 4711);
        verify(atomicNumber).set(4711);
    }

    @Test
    public void testGet() {
        when(atomicNumber.get()).thenReturn(1234L);
        long body = template.requestBody("direct:get", null, Long.class);
        verify(atomicNumber).get();
        assertEquals(1234, body);
    }

    @Test
    public void testIncrement() {
        when(atomicNumber.incrementAndGet()).thenReturn(11L);
        long body = template.requestBody("direct:increment", null, Long.class);
        verify(atomicNumber).incrementAndGet();
        assertEquals(11, body);
    }

    @Test
    public void testDecrement() {
        when(atomicNumber.decrementAndGet()).thenReturn(9L);
        long body = template.requestBody("direct:decrement", null, Long.class);
        verify(atomicNumber).decrementAndGet();
        assertEquals(9, body);
    }

    @Test
    public void testDestroy() throws InterruptedException {
        template.sendBody("direct:destroy", null);
        verify(atomicNumber).destroy();
    }

    @Test
    public void testSetWithOperationNumber() {
        template.sendBody("direct:setWithOperationNumber", 5711);
        verify(atomicNumber).set(5711);
    }

    @Test
    public void testSetWithOperationName() {
        template.sendBody("direct:setWithOperationName", 5711);
        verify(atomicNumber).set(5711);
    }
    
    @Test
    public void testCompareAndSet() {
        Map<String, Object> headersOk = new HashMap();
        headersOk.put(HazelcastConstants.EXPECTED_VALUE, 1234L);
        when(atomicNumber.compareAndSet(1234L, 1235L)).thenReturn(true);
        when(atomicNumber.compareAndSet(1233L, 1235L)).thenReturn(false);
        boolean result = template.requestBodyAndHeaders("direct:compareAndSet", 1235L, headersOk, Boolean.class);
        verify(atomicNumber).compareAndSet(1234L, 1235L);
        assertEquals(true, result);
        Map<String, Object> headersKo = new HashMap();
        headersKo.put(HazelcastConstants.EXPECTED_VALUE, 1233L);
        result = template.requestBodyAndHeaders("direct:compareAndSet", 1235L, headersKo, Boolean.class);
        verify(atomicNumber).compareAndSet(1233L, 1235L);
        assertEquals(false, result);
    }
    
    @Test
    public void testGetAndAdd() {
        when(atomicNumber.getAndAdd(12L)).thenReturn(13L);
        long result = template.requestBody("direct:getAndAdd", 12L, Long.class);
        verify(atomicNumber).getAndAdd(12L);
        assertEquals(13L, result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("direct:setInvalid").setHeader(HazelcastConstants.OPERATION, constant("invalid"))
                        .to(String.format("hazelcast-%sfoo", HazelcastConstants.ATOMICNUMBER_PREFIX));

                from("direct:set").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.SET_VALUE))
                        .to(String.format("hazelcast-%sfoo", HazelcastConstants.ATOMICNUMBER_PREFIX));

                from("direct:get").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.GET)).to(String.format("hazelcast-%sfoo", HazelcastConstants.ATOMICNUMBER_PREFIX));

                from("direct:increment").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.INCREMENT)).to(
                        String.format("hazelcast-%sfoo", HazelcastConstants.ATOMICNUMBER_PREFIX));

                from("direct:decrement").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.DECREMENT)).to(
                        String.format("hazelcast-%sfoo", HazelcastConstants.ATOMICNUMBER_PREFIX));

                from("direct:destroy").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.DESTROY)).to(
                        String.format("hazelcast-%sfoo", HazelcastConstants.ATOMICNUMBER_PREFIX));
                
                from("direct:compareAndSet").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.COMPARE_AND_SET)).to(
                        String.format("hazelcast-%sfoo", HazelcastConstants.ATOMICNUMBER_PREFIX));
              
                from("direct:getAndAdd").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.GET_AND_ADD)).to(
                        String.format("hazelcast-%sfoo", HazelcastConstants.ATOMICNUMBER_PREFIX));

                from("direct:setWithOperationNumber").toF("hazelcast-%sfoo?operation=%s", HazelcastConstants.ATOMICNUMBER_PREFIX, HazelcastOperation.SET_VALUE);
                from("direct:setWithOperationName").toF("hazelcast-%sfoo?operation=setvalue", HazelcastConstants.ATOMICNUMBER_PREFIX);

            }
        };
    }

}
