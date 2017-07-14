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

import java.util.ArrayList;
import java.util.Collection;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ISet;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.After;
import org.junit.Test;
import org.mockito.Mock;

import static org.mockito.Mockito.*;

public class HazelcastSetProducerTest extends HazelcastCamelTestSupport {

    @Mock
    private ISet<String> set;

    @Override
    protected void trainHazelcastInstance(HazelcastInstance hazelcastInstance) {
        when(hazelcastInstance.<String>getSet("bar")).thenReturn(set);
    }

    @Override
    protected void verifyHazelcastInstance(HazelcastInstance hazelcastInstance) {
        verify(hazelcastInstance, atLeastOnce()).getSet("bar");
    }

    @After
    public final void verifySetMock() {
        verifyNoMoreInteractions(set);
    }

    @Test(expected = CamelExecutionException.class)
    public void testWithInvalidOperation() {
        template.sendBody("direct:addInvalid", "bar");
    }

    @Test
    public void addValue() throws InterruptedException {
        template.sendBody("direct:add", "bar");
        verify(set).add("bar");
    }

    @Test
    public void addValueWithOperationNumber() throws InterruptedException {
        template.sendBody("direct:addWithOperationNumber", "bar");
        verify(set).add("bar");
    }

    @Test
    public void addValueWithOperationName() throws InterruptedException {
        template.sendBody("direct:addWithOperationName", "bar");
        verify(set).add("bar");
    }

    @Test
    public void removeValue() throws InterruptedException {
        template.sendBody("direct:removeValue", "foo2");
        verify(set).remove("foo2");
    }
    
    @Test
    public void clearList() {
        template.sendBody("direct:clear", "");
        verify(set).clear();
    }
    
    @Test
    public void addAll() throws InterruptedException {
        Collection t = new ArrayList();
        t.add("test1");
        t.add("test2");
        template.sendBody("direct:addall", t);
        verify(set).addAll(t);
    }
    
    @Test
    public void removeAll() throws InterruptedException {
        Collection t = new ArrayList();
        t.add("test1");
        t.add("test2");
        template.sendBody("direct:removeAll", t);
        verify(set).removeAll(t);
    }
    
    @Test
    public void retainAll() throws InterruptedException {
        Collection t = new ArrayList();
        t.add("test1");
        t.add("test2");
        template.sendBody("direct:RETAIN_ALL", t);
        verify(set).retainAll(t);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("direct:addInvalid").setHeader(HazelcastConstants.OPERATION, constant("bogus")).toF("hazelcast-%sbar", HazelcastConstants.SET_PREFIX);

                from("direct:add").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.ADD)).toF("hazelcast-%sbar", HazelcastConstants.SET_PREFIX);

                from("direct:removeValue").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.REMOVE_VALUE)).to(
                        String.format("hazelcast-%sbar", HazelcastConstants.SET_PREFIX));
                
                from("direct:clear").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.CLEAR)).toF("hazelcast-%sbar", HazelcastConstants.SET_PREFIX);

                from("direct:addall").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.ADD_ALL)).to(
                        String.format("hazelcast-%sbar", HazelcastConstants.SET_PREFIX));
                
                from("direct:removeAll").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.REMOVE_ALL)).to(
                        String.format("hazelcast-%sbar", HazelcastConstants.SET_PREFIX));
                
                from("direct:RETAIN_ALL").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.RETAIN_ALL)).to(
                        String.format("hazelcast-%sbar", HazelcastConstants.SET_PREFIX));
                
                from("direct:addWithOperationNumber").toF("hazelcast-%sbar?operation=%s", HazelcastConstants.SET_PREFIX, HazelcastOperation.ADD);
                from("direct:addWithOperationName").toF("hazelcast-%sbar?operation=ADD", HazelcastConstants.SET_PREFIX);
            }
        };
    }

}

