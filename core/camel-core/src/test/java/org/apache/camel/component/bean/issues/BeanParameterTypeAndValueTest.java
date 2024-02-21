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
package org.apache.camel.component.bean.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

public class BeanParameterTypeAndValueTest extends ContextTestSupport {

    @Test
    public void testBean() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("123");
        getMockEndpoint("mock:result").message(0).body().isInstanceOf(Integer.class);

        template.sendBody("direct:bean", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testBean2() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("44");
        getMockEndpoint("mock:result").message(0).body().isInstanceOf(Integer.class);

        template.sendBody("direct:bean2", "Hi World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSimple() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("7");
        getMockEndpoint("mock:result").message(0).body().isInstanceOf(Integer.class);

        template.sendBody("direct:simple", "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:bean")
                        .bean(Math.class, "abs(int.class -123)")
                        .to("mock:result");

                from("direct:bean2")
                        .bean("type:java.lang.Math", "abs(int.class -44)")
                        .to("mock:result");

                from("direct:simple")
                        .setBody().simple("${bean:type:java.lang.Math?method=abs(int.class -7)}")
                        .to("mock:result");
            }
        };
    }
}
