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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ClaimCheckOperation;
import org.junit.Test;

public class ClaimCheckEipPushPopHeadersTest extends ContextTestSupport {

    @Test
    public void testPushPopHeaders() throws Exception {
        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:a").expectedHeaderReceived("foo", 123);
        getMockEndpoint("mock:b").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:b").expectedHeaderReceived("foo", 456);
        getMockEndpoint("mock:c").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:c").expectedHeaderReceived("foo", 123);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", 123);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("mock:a")
                    .claimCheck(ClaimCheckOperation.Push)
                    .transform().constant("Bye World")
                    .setHeader("foo", constant(456))
                    .to("mock:b")
                    // only merge in the message headers
                    .claimCheck(ClaimCheckOperation.Pop, null, "headers")
                    .to("mock:c");
            }
        };
    }
}
