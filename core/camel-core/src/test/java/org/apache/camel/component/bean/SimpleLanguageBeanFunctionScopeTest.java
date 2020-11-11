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
package org.apache.camel.component.bean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.SimpleRegistry;
import org.junit.jupiter.api.Test;

public class SimpleLanguageBeanFunctionScopeTest extends ContextTestSupport {

    @Test
    public void testSingleton() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("A", "B");
        getMockEndpoint("mock:other").expectedBodiesReceived("C");

        template.sendBody("direct:single", "A");
        template.sendBody("direct:single", "B");
        template.sendBody("direct:single", "C");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testPrototype() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("A", "B", "C");

        template.sendBody("direct:proto", "A");
        template.sendBody("direct:proto", "B");
        template.sendBody("direct:proto", "C");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRequest() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("A", "A", "B", "B", "C", "C");
        getMockEndpoint("mock:other").expectedBodiesReceived("A", "B", "C");

        template.sendBody("direct:request", "A");
        template.sendBody("direct:request", "B");
        template.sendBody("direct:request", "C");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry wrapper = new SimpleRegistry() {
            @Override
            public Object lookupByName(String name) {
                // create a new instance so its prototype scoped from the backing registry
                if ("foo".equals(name)) {
                    return new MyBean();
                }
                return super.lookupByName(name);
            }
        };
        return wrapper;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:single").choice().when().simple("${bean:foo?scope=Singleton}").to("mock:result")
                        .otherwise().to("mock:other");

                from("direct:proto").choice().when().simple("${bean:foo?scope=Prototype}").to("mock:result")
                        .otherwise().to("mock:other");

                from("direct:request")
                        .to("direct:sub")
                        .to("direct:sub")
                        .to("direct:sub");

                from("direct:sub").choice().when().simple("${bean:foo?scope=Request}").to("mock:result")
                        .otherwise().to("mock:other");

            }
        };
    }

    public static class MyBean {

        private int counter;

        public boolean bar(String body) {
            return ++counter < 3;
        }
    }

}
