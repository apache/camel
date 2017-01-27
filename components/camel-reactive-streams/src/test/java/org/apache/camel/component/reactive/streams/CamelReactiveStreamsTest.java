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
package org.apache.camel.component.reactive.streams;

import org.apache.camel.component.reactive.streams.api.CamelReactiveStreams;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.apache.camel.component.reactive.streams.engine.CamelReactiveStreamsServiceImpl;
import org.apache.camel.component.reactive.streams.support.ReactiveStreamsTestService;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;


public class CamelReactiveStreamsTest extends CamelTestSupport {

    @Test
    public void testDefaultService() {
        CamelReactiveStreamsService service1 = CamelReactiveStreams.get(context, "default-service");
        assertTrue(service1 instanceof CamelReactiveStreamsServiceImpl);
    }

    @Test
    public void testSameDefaultServiceReturned() {
        CamelReactiveStreamsService service1 = CamelReactiveStreams.get(context, "default-service");
        CamelReactiveStreamsService service2 = CamelReactiveStreams.get(context, "default-service");
        assertTrue(service1 instanceof CamelReactiveStreamsServiceImpl);
        assertEquals(service1, service2);
    }

    @Test
    public void testSameServiceReturnedFromRegistry() {
        CamelReactiveStreamsService service1 = CamelReactiveStreams.get(context);
        CamelReactiveStreamsService service2 = CamelReactiveStreams.get(context);

        assertEquals(service1, service2);
        assertTrue(service1 instanceof ReactiveStreamsTestService);
        assertEquals("from-registry", ((ReactiveStreamsTestService) service1).getName());
    }

    @Test
    public void testSameNamedServiceReturnedFromRegistry() {
        CamelReactiveStreamsService service1 = CamelReactiveStreams.get(context, "dummy");
        CamelReactiveStreamsService service2 = CamelReactiveStreams.get(context, "dummy");

        assertEquals(service1, service2);
        assertTrue(service1 instanceof ReactiveStreamsTestService);
        assertEquals("from-registry", ((ReactiveStreamsTestService) service1).getName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOnlyOneService() {
        CamelReactiveStreams.get(context);
        CamelReactiveStreams.get(context, "dummy");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOnlyOneNamedService() {
        CamelReactiveStreams.get(context, "dummy");
        CamelReactiveStreams.get(context, "dummy2");
    }

    @Test
    public void testNamedServiceResolvedUsingFactory() {
        CamelReactiveStreamsService service1 = CamelReactiveStreams.get(context, "test-service");
        assertTrue(service1 instanceof ReactiveStreamsTestService);
        assertNull(((ReactiveStreamsTestService) service1).getName());
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("dummy", new ReactiveStreamsTestService("from-registry"));
        return registry;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
