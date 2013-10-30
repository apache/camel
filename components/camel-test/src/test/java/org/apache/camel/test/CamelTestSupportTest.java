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
package org.apache.camel.test;

import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;

public class CamelTestSupportTest extends CamelTestSupport {

    private volatile boolean called;

    @Override
    @Before
    public void setUp() throws Exception {
        called = false;
        replaceRouteFromWith("routeId", "direct:start");
        super.setUp();
    }

    @Test
    public void replacesFromEndpoint() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test(expected = NoSuchEndpointException.class)
    public void exceptionThrownWhenEndpointNotFoundAndNoCreate() {
        getMockEndpoint("mock:bogus", false);
    }

    @Test(expected = NoSuchEndpointException.class)
    public void exceptionThrownWhenEndpointNotAMockEndpoint() {
        getMockEndpoint("direct:something", false);
    }

    @Test
    public void autoCreateNoneExisting() {
        MockEndpoint mock = getMockEndpoint("mock:bogus2", true);
        assertNotNull(mock);
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        called = true;

        JndiRegistry jndi = super.createRegistry();
        jndi.bind("beer", "yes");
        return jndi;
    }

    @Test
    public void testCreateRegistry() {
        assertTrue("Should call createRegistry", called);
        assertEquals("yes", context.getRegistry().lookupByName("beer"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:something")
                        .id("routeId")
                        .to("mock:result");
            }
        };
    }
}
