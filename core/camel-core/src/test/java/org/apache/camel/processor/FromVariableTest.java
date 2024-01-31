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
package org.apache.camel.processor;

import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FromVariableTest extends ContextTestSupport {

    @Test
    public void testOriginalBody() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("Bye ");
        getMockEndpoint("mock:result").expectedBodiesReceived("World");

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOriginalHeaders() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("Bye ");
        getMockEndpoint("mock:foo").expectedHeaderReceived("foo", 456);
        getMockEndpoint("mock:foo").whenAnyExchangeReceived(e -> {
            Map m = e.getVariable("header:myKey", Map.class);
            Assertions.assertNotNull(m);
            Assertions.assertEquals(1, m.size());
            Assertions.assertEquals(123, m.get("foo"));
        });

        getMockEndpoint("mock:result").expectedBodiesReceived("World");
        getMockEndpoint("mock:result").expectedHeaderReceived("foo", 456);

        template.sendBodyAndHeader("direct:start", "World", "foo", 123);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                fromV("direct:start", "myKey")
                        .setHeader("foo", constant(456))
                        .setHeader("bar", constant("Murphy"))
                        .transform().simple("Bye ${body}")
                        .to("mock:foo")
                        .setBody(simple("${variable:myKey}"))
                        .to("mock:result");
            }
        };
    }
}
