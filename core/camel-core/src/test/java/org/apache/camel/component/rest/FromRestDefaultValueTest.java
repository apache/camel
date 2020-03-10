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
package org.apache.camel.component.rest;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.spi.Registry;
import org.junit.Test;

public class FromRestDefaultValueTest extends ContextTestSupport {

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("dummy-rest", new DummyRestConsumerFactory());
        return jndi;
    }

    @Test
    public void testDefaultValue() throws Exception {
        getMockEndpoint("mock:bye").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:bye").expectedHeaderReceived("kind", "customer");

        // the rest becomes routes and the input is a seda endpoint created by
        // the DummyRestConsumerFactory
        template.sendBody("seda:get-say-bye", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDefaultValueOverride() throws Exception {
        getMockEndpoint("mock:bye").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:bye").expectedHeaderReceived("kind", "admin");

        // the rest becomes routes and the input is a seda endpoint created by
        // the DummyRestConsumerFactory
        template.sendBodyAndHeader("seda:get-say-bye", "Bye World", "kind", "admin");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration().host("localhost").enableCORS(true);

                rest("/say/bye").consumes("application/json").get().param().type(RestParamType.query).name("kind").defaultValue("customer").endParam().to("mock:bye");
            }
        };
    }
}
