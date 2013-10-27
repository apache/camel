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

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;

/**
 * @version 
 */
public class CBRPredicateBeanThrowExceptionTest extends ContextTestSupport {

    private static AtomicBoolean check = new AtomicBoolean();
    private static AtomicBoolean check2 = new AtomicBoolean();

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("cbrBean", new MyCBRBean());
        return jndi;
    }

    public void testCBR() throws Exception {
        check.set(false);
        check2.set(false);

        getMockEndpoint("mock:dead").expectedMessageCount(0);
        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello Foo");
        getMockEndpoint("mock:bar").expectedBodiesReceived("Hello Bar");

        template.sendBodyAndHeader("direct:start", "Hello Foo", "foo", "bar");
        template.sendBodyAndHeader("direct:start", "Hello Bar", "foo", "other");

        assertMockEndpointsSatisfied();

        assertTrue(check.get());
        assertTrue(check2.get());
    }

    public void testCBRKaboom() throws Exception {
        check.set(false);
        check2.set(false);

        getMockEndpoint("mock:foo").expectedMessageCount(0);
        getMockEndpoint("mock:foo2").expectedMessageCount(0);
        getMockEndpoint("mock:bar").expectedMessageCount(0);
        getMockEndpoint("mock:dead").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello Foo", "foo", "Kaboom");

        assertMockEndpointsSatisfied();

        assertTrue(check.get());
        assertFalse(check2.get());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead"));

                from("direct:start")
                    .choice()
                        .when().method("cbrBean", "checkHeader")
                            .to("mock:foo")
                        .when().method("cbrBean", "checkHeader2")
                            .to("mock:foo2")
                        .otherwise()
                            .to("mock:bar")
                    .end();
            }
        };
    }

    public static class MyCBRBean {

        public boolean checkHeader(Exchange exchange) {
            check.set(true);

            Message inMsg = exchange.getIn();
            String foo = (String) inMsg.getHeader("foo");

            if ("Kaboom".equalsIgnoreCase(foo)) {
                throw new IllegalArgumentException("Forced");
            }

            return foo.equals("bar");
        }

        public boolean checkHeader2(Exchange exchange) {
            check2.set(true);

            Message inMsg = exchange.getIn();
            String foo = (String) inMsg.getHeader("foo");

            if ("Kaboom".equalsIgnoreCase(foo)) {
                throw new IllegalArgumentException("Forced");
            }

            return foo.equals("bar");
        }
    }
}

