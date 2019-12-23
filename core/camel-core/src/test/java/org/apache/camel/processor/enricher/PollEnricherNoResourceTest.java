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
package org.apache.camel.processor.enricher;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class PollEnricherNoResourceTest extends ContextTestSupport {

    @Test
    public void testNoResourceA() throws Exception {
        // there should be no message body
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").message(0).body().isNull();
        getMockEndpoint("mock:result").expectedHeaderReceived(Exchange.TO_ENDPOINT, "seda://foo");

        template.sendBody("direct:a", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testResourceA() throws Exception {
        template.sendBody("seda:foo", "Bye World");

        Thread.sleep(250);

        // there should be a message body
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:result").expectedHeaderReceived(Exchange.TO_ENDPOINT, "seda://foo");

        template.sendBody("direct:a", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testResourceB() throws Exception {
        template.sendBody("seda:bar", "Bye World");

        // there should be a message body
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:result").expectedHeaderReceived(Exchange.TO_ENDPOINT, "seda://bar");

        template.sendBody("direct:b", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:a").pollEnrich("seda:foo", 1000).to("mock:result");

                from("direct:b").pollEnrich("seda:bar").to("mock:result");
            }
        };
    }

}
