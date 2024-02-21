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
import org.apache.camel.Handler;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

public class MethodCallByTypeRefExistingBeanTest extends ContextTestSupport {

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("foo", new MyBean("Type Ref "));
        return jndi;
    }

    @Test
    public void testRefOrBeanPrefix() throws Exception {
        getMockEndpoint("mock:a").expectedBodiesReceived("Hello Type Ref A");
        getMockEndpoint("mock:b").expectedBodiesReceived("Hello Type Ref B");

        template.sendBody("direct:a", "A");
        template.sendBody("direct:b", "B");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:a").transform().method(MyBean.class).to("mock:a");

                from("direct:b").transform().method(MyBean.class).to("mock:b");
            }
        };
    }

    private static class MyBean {

        private final String field;

        //No default constructor available
        public MyBean(String field) {
            this.field = field;
        }

        @Handler
        public String hello(String text) {
            return "Hello " + field + text;
        }

    }
}
