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
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;

/**
 * @version 
 */
public class CBRHeaderPredicateTest extends ContextTestSupport {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("cbrBean", new MyCBRBean());
        return jndi;
    }

    public void testCBR() throws Exception {
        MockEndpoint foo = getMockEndpoint("mock:foo");
        foo.expectedBodiesReceived("Hello Foo");

        MockEndpoint bar = getMockEndpoint("mock:bar");
        bar.expectedBodiesReceived("Hello Bar");

        template.sendBodyAndHeader("direct:start", "Hello Foo", "foo", "bar");
        template.sendBodyAndHeader("direct:start", "Hello Bar", "foo", "other");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .choice()
                        .when().method("cbrBean", "checkHeader")
                            .to("mock:foo")
                        .otherwise()
                            .to("mock:bar")
                    .end();
            }
        };
    }

    public static class MyCBRBean {

        public boolean checkHeader(Exchange exchange) {
            Message inMsg = exchange.getIn();
            String foo = (String) inMsg.getHeader("foo");
            return foo.equals("bar");
        }
    }
}

