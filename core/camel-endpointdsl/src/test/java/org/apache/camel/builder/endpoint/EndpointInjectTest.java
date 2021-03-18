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

import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.builder.EndpointProducerBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.apache.camel.builder.endpoint.StaticEndpointBuilders.mock;

/**
 * Its not exactly @EndpointInject but we can simulate it via endpoint builders
 */
public class EndpointInjectTest extends BaseEndpointDslTest {

    private final EndpointProducerBuilder foo = mock("result").expectedCount(3);

    @Test
    public void testEndpointInject() throws Exception {
        FluentProducerTemplate ft = context.createFluentProducerTemplate();
        ft.to(foo).withBody("World").send();

        template.sendBody("seda:foo", "Hello");
        template.sendBody("direct:foo", "Camel");

        foo.resolve(context, MockEndpoint.class).assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new EndpointRouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(direct("foo"))
                        .to(seda("foo"));
                from(seda("foo"))
                        .to(foo);
            }
        };
    }
}
