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
public class ToAsyncTest extends ContextTestSupport {

    public void testToAsync() throws Exception {
        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:b").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").message(0).outBody(String.class).isEqualTo("Bye World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        // and it should be different exchange ids

        String ida = getMockEndpoint("mock:a").getReceivedExchanges().get(0).getExchangeId();
        String idb = getMockEndpoint("mock:b").getReceivedExchanges().get(0).getExchangeId();
        String idresult = getMockEndpoint("mock:result").getReceivedExchanges().get(0).getExchangeId();

        // id a should be different and id b and id result the same
        assertNotSame(ida, idb);
        assertNotSame(ida, idresult);
        assertSame(idb, idresult);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("mock:a").toAsync("direct:bar").to("mock:result");

                from("direct:bar").to("mock:b").transform(constant("Bye World"));
            }
        };
    }
}
