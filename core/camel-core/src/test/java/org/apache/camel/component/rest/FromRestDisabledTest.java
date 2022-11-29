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
import org.apache.camel.spi.Registry;
import org.apache.camel.support.CamelContextHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FromRestDisabledTest extends ContextTestSupport {

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("dummy-rest", new DummyRestConsumerFactory());
        return jndi;
    }

    @Test
    public void testDisabled() throws Exception {
        getMockEndpoint("mock:bye").expectedBodiesReceived("Hello World");
        template.sendBody("seda:get-say-bye", "Hello World");
        assertMockEndpointsSatisfied();

        // should only be 2 route as 1 is disabled
        Assertions.assertEquals(2, context.getRoutes().size());

        // should NOT have mock://hi endpoint
        Assertions.assertEquals(0, CamelContextHelper.getEndpoints(context, e -> e.getEndpointUri().contains("hi")).size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration().host("localhost");

                rest("/say")
                    .post("/hi").to("mock:hi").disabled()
                    .get("/bye").to("mock:bye")
                    .get("/translate").to("mock:translate");
            }
        };
    }
}
