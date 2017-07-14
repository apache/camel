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
package org.apache.camel.language;

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.PropertyInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.properties.PropertiesComponent;

/**
 *
 */
public class PropertyInjectAnnotationParameterTest extends ContextTestSupport {

    public void testPropertyInjectAnnotationOne() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:one", "World");

        assertMockEndpointsSatisfied();
    }

    public void testPropertyInjectAnnotationTwo() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("WorldWorldWorld");

        template.sendBody("direct:two", "World");

        assertMockEndpointsSatisfied();
    }

    public void testPropertyInjectAnnotationThree() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Goodbye World");

        template.sendBody("direct:three", "World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        PropertiesComponent pc = new PropertiesComponent();
        Properties props = new Properties();
        props.put("greeting", "Hello");
        props.put("times", "3");
        pc.setInitialProperties(props);
        context.addComponent("properties", pc);

        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:one")
                    .bean(MyBean.class)
                    .to("mock:result");

                from("direct:two")
                    .bean(MyCounterBean.class)
                    .to("mock:result");

                from("direct:three")
                    .bean(MyOtherBean.class)
                    .to("mock:result");
            }
        };
    }

    public static final class MyBean {

        public String callA(@PropertyInject("greeting") String greeting, String body) {
            return greeting + " " + body;
        }

    }

    public static final class MyOtherBean {

        public String callA(@PropertyInject(value = "bye", defaultValue = "Goodbye") String bye, String body) {
            return bye + " " + body;
        }

    }

    public static final class MyCounterBean {

        public String callA(@PropertyInject("times") int times, String body) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < times; i++) {
                sb.append(body);
            }
            return sb.toString();
        }

    }

}
