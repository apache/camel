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
package org.apache.camel.processor.async;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version $Revision$
 */
public class ToAsyncTwoTest extends ContextTestSupport {

    public void testToAsyncTwo() throws Exception {
        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:b").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:c").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:d").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:e").expectedBodiesReceived("Bye Again World");
        getMockEndpoint("mock:result").expectedBodiesReceived("Hi World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        // and it should be different exchange ids

        String ida = getMockEndpoint("mock:a").getReceivedExchanges().get(0).getExchangeId();
        String idb = getMockEndpoint("mock:b").getReceivedExchanges().get(0).getExchangeId();
        String idc = getMockEndpoint("mock:c").getReceivedExchanges().get(0).getExchangeId();
        String idd = getMockEndpoint("mock:d").getReceivedExchanges().get(0).getExchangeId();
        String ide = getMockEndpoint("mock:e").getReceivedExchanges().get(0).getExchangeId();
        String idresult = getMockEndpoint("mock:result").getReceivedExchanges().get(0).getExchangeId();

        // ids on exchanges should be different in groups
        assertSame(idb, idc);
        assertSame(idd, idresult);
        assertSame(idd, ide);
        assertNotNull(ida, idb);
        assertNotNull(idb, idd);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("mock:a")
                    .toAsync("direct:bar", 5).to("mock:c")
                    .toAsync("direct:foo", 2).to("mock:e").transform(constant("Hi World")).to("mock:result");

                from("direct:bar").to("mock:b").transform(constant("Bye World"));

                from("direct:foo").to("mock:d").transform(constant("Bye Again World"));
            }
        };
    }
}