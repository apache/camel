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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ClaimCheckOperation;
import org.junit.Test;

public class MulticastMixOriginalMessageBodyAndEnrichedHeadersClaimCheckTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testMulticastMixOriginalAndHeaders() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class).handled(true)
                    // merge the message with the original message body but keep
                    // any existing headers
                    // (we could also use Push/Pop operation instead, then
                    // without using the "myOriginalBody" key)
                    .claimCheck(ClaimCheckOperation.Get, "myOriginalBody", "body").to("mock:b");

                from("direct:start")
                    // we want to preserve the real original message body and
                    // then include other headers that have been
                    // set later during routing
                    // (we could also use Push/Pop operation instead, then
                    // without using the "myOriginalBody" key)
                    .claimCheck(ClaimCheckOperation.Set, "myOriginalBody").setBody(constant("Changed body")).setHeader("foo", constant("bar")).multicast().stopOnException()
                    .to("direct:a").to("direct:b").end();

                from("direct:a").to("mock:a");

                from("direct:b").to("mock:c").throwException(new IllegalArgumentException("Forced"));
            }
        });
        context.start();

        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:a").expectedBodiesReceived("Changed body");
        getMockEndpoint("mock:a").expectedHeaderReceived("foo", "bar");
        getMockEndpoint("mock:b").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:b").expectedHeaderReceived("foo", "bar");
        getMockEndpoint("mock:c").expectedMessageCount(1);
        getMockEndpoint("mock:c").expectedHeaderReceived("foo", "bar");
        getMockEndpoint("mock:c").expectedBodiesReceived("Changed body");
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

}
