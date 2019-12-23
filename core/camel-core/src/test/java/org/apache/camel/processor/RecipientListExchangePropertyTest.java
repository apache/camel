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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class RecipientListExchangePropertyTest extends ContextTestSupport {

    private final MyStuff myStuff = new MyStuff("Blah");

    @Test
    public void testExchangeProperty() throws Exception {
        getMockEndpoint("mock:x").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:x").message(0).exchangeProperty("foo").isEqualTo(myStuff);
        getMockEndpoint("mock:y").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:y").message(0).exchangeProperty("foo").isEqualTo(myStuff);
        getMockEndpoint("mock:z").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:z").message(0).exchangeProperty("foo").isEqualTo(myStuff);
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result").message(0).exchangeProperty("foo").isEqualTo(myStuff);

        template.sendBodyAndProperty("direct:a", "Hello World", "foo", myStuff);

        assertMockEndpointsSatisfied();

        MyStuff stuff = getMockEndpoint("mock:result").getReceivedExchanges().get(0).getProperty("foo", MyStuff.class);
        assertSame("Should be same instance", myStuff, stuff);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:a").recipientList(constant("mock:x,mock:y,mock:z")).to("mock:result");
            }
        };

    }

    private static final class MyStuff {

        private String name;

        private MyStuff(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

}
