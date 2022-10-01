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
package org.apache.camel.builder.endpoint;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.seda.SedaComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CustomSedaTest extends BaseEndpointDslTest {

    @Test
    public void testCustomSeda() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("Hello", "World", "Camel");

        template.sendBody("seda:foo", "Hello");
        template.sendBody("seda2:foo", "World");
        template.sendBody("direct:foo", "Camel");

        MockEndpoint.assertIsSatisfied(context);

        assertNotNull(context.hasComponent("seda"));
        assertNotNull(context.hasComponent("seda2"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new EndpointRouteBuilder() {
            @Override
            public void configure() throws Exception {
                SedaComponent seda2 = new SedaComponent();
                context.addComponent("seda2", seda2);

                from(seda("foo"))
                        .to(mock("result"));

                from(seda("seda2", "foo"))
                        .to(mock("result"));

                from(direct("foo"))
                        .to(seda("seda2", "foo"));
            }
        };
    }
}
