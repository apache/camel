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
package org.apache.camel.component.rest;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.FooBar;
import org.apache.camel.impl.JndiRegistry;

public class FromRestConfigurationTest extends FromRestGetTest {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myDummy", new FooBar());
        return jndi;
    }

    @Override
    public void testFromRestModel() throws Exception {
        super.testFromRestModel();

        assertEquals("dummy-rest", context.getRestConfiguration().getComponent());
        assertEquals("localhost", context.getRestConfiguration().getHost());
        assertEquals(9090, context.getRestConfiguration().getPort());
        assertEquals("bar", context.getRestConfiguration().getComponentProperties().get("foo"));
        assertEquals("stuff", context.getRestConfiguration().getComponentProperties().get("other"));
        assertEquals("200", context.getRestConfiguration().getEndpointProperties().get("size"));
        assertEquals("1000", context.getRestConfiguration().getConsumerProperties().get("pollTimeout"));
        assertEquals("#myDummy", context.getRestConfiguration().getConsumerProperties().get("dummy"));

        DummyRestConsumerFactory factory = (DummyRestConsumerFactory) context.getRegistry().lookupByName("dummy-rest");

        Object dummy = context.getRegistry().lookupByName("myDummy");
        assertSame(dummy, factory.getDummy());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        final RouteBuilder lowerR = super.createRouteBuilder();
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration().component("dummy-rest").host("localhost").port(9090)
                    .componentProperty("foo", "bar")
                    .componentProperty("other", "stuff")
                    .endpointProperty("size", "200")
                    .consumerProperty("pollTimeout", "1000")
                    .consumerProperty("dummy", "#myDummy");

                includeRoutes(lowerR);
            }
        };
    }
}
