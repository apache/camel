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
import org.junit.After;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.mockito.Mockito.*;

public class HazelcastAtomicnumberProducerForSpringTest extends HazelcastCamelSpringTestSupport {

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
        verify(hazelcastInstance, times(7)).getCPSubsystem();
        verify(cpSubsystem, atLeastOnce()).getAtomicLong("foo");
    }

    @After
    public void verifyAtomicNumberMock() {
        verifyNoMoreInteractions(atomicNumber);
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("/META-INF/spring/test-camel-context-atomicnumber.xml");
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
}
