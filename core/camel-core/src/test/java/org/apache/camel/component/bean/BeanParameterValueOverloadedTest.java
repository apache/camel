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
import org.junit.Test;

/**
 *
 */
public class BeanParameterValueOverloadedTest extends ContextTestSupport {

    @Test
    public void testBeanParameterValueBoolean() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testBeanParameterValueBoolean2() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start2", "World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("foo", new MyBean());
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("bean:foo?method=bar(*,true)").to("mock:result");

                from("direct:start2").to("bean:foo?method=bar(${body},false,true)").to("mock:result");
            }
        };
    }

    public static class MyBean {

        public String bar(String body, boolean hello) {
            if (hello) {
                return "Hello " + body;
            } else {
                return body;
            }
        }

        public String bar(String body, boolean hello, boolean bye) {
            if (hello) {
                return "Hi " + body;
            } else if (bye) {
                return "Bye " + body;
            } else {
                return body;
            }
        }

    }
}
