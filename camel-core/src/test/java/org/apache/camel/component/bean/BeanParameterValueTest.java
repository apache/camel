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
package org.apache.camel.component.bean;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;

/**
 *
 */
public class BeanParameterValueTest extends ContextTestSupport {

    public void testBeanParameterValueBoolean() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }

    public void testBeanParameterValueBoolean2() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start2", "World");

        assertMockEndpointsSatisfied();
    }

    public void testBeanParameterValueBoolean3() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start3", "World");

        assertMockEndpointsSatisfied();
    }

    public void testBeanParameterValueBoolean4() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello Camel");

        template.sendBody("direct:start4", "World");

        assertMockEndpointsSatisfied();
    }

    public void testBeanParameterValueInteger() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("WorldWorldWorld");

        template.sendBody("direct:echo", "World");

        assertMockEndpointsSatisfied();
    }

    public void testBeanParameterValueHeaderInteger() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("WorldWorld");

        template.sendBodyAndHeader("direct:echo2", "World", "times", 2);

        assertMockEndpointsSatisfied();
    }

    public void testBeanParameterValueMap() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("direct:heads", "World", "hello", "Hello");

        assertMockEndpointsSatisfied();
    }

    public void testBeanParameterNoBody() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Is Hadrian 21 years old?");

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("SomeTest", true);
        headers.put("SomeAge", 21);
        headers.put("SomeName", "Hadrian");

        template.sendBodyAndHeaders("direct:nobody", null, headers);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("foo", new MyBean());
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("bean:foo?method=bar(*,true)")
                    .to("mock:result");

                from("direct:start2")
                    .to("bean:foo?method=bar(${body},true)")
                    .to("mock:result");

                from("direct:start3")
                    .to("bean:foo?method=bar(${body}, true)")
                    .to("mock:result");

                from("direct:start4")
                    .to("bean:foo?method=bar('Camel', true)")
                    .to("mock:result");

                from("direct:echo")
                    .to("bean:foo?method=echo(*, 3)")
                    .to("mock:result");

                from("direct:echo2")
                    .to("bean:foo?method=echo(*, ${in.header.times})")
                    .to("mock:result");

                from("direct:heads")
                    .to("bean:foo?method=heads(${body}, ${headers})")
                    .to("mock:result");

                from("direct:nobody")
                    .to("bean:foo?method=nobody(${header.SomeAge}, ${header.SomeName}, ${header.SomeTest})")
                    .to("mock:result");
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

        public String echo(String body, int times) {
            if (times > 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < times; i++) {
                    sb.append(body);
                }
                return sb.toString();
            }

            return body;
        }

        public String heads(String body, Map<?, ?> headers) {
            return headers.get("hello") + " " + body;
        }

        public String nobody(int age, String name, boolean question) {
            StringBuilder sb = new StringBuilder();
            sb.append(question ? "Is " : "");
            sb.append(name);
            sb.append(question ? " " : "is ");
            sb.append(age);
            sb.append(" years old");
            sb.append(question ? "?" : ".");
            return sb.toString();
        }
    }
}
