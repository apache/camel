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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClassComponentScopeTest extends ContextTestSupport {

    @Test
    public void testDefaultSingletonScope() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello A", "Hello B");

        int before = MyCountingBean.getInstanceCount();
        template.sendBody("direct:singleton", "A");
        template.sendBody("direct:singleton", "B");

        assertMockEndpointsSatisfied();

        assertEquals(0, MyCountingBean.getInstanceCount() - before,
                "Singleton scope should not create new bean instances per exchange");
    }

    @Test
    public void testPrototypeScope() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello A", "Hello B");

        int before = MyCountingBean.getInstanceCount();
        template.sendBody("direct:prototype", "A");
        template.sendBody("direct:prototype", "B");

        assertMockEndpointsSatisfied();

        assertEquals(2, MyCountingBean.getInstanceCount() - before,
                "Prototype scope should create a new bean per exchange");
    }

    @Test
    public void testSingletonScopeWithBeanOptions() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye A", "Bye B");

        int before = MyCountingBean.getInstanceCount();
        template.sendBody("direct:singletonWithOptions", "A");
        template.sendBody("direct:singletonWithOptions", "B");

        assertMockEndpointsSatisfied();

        assertEquals(0, MyCountingBean.getInstanceCount() - before,
                "Singleton scope with bean options should not create new bean instances per exchange");
    }

    @Test
    public void testPrototypeScopeWithBeanOptions() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye A", "Bye B");

        int before = MyCountingBean.getInstanceCount();
        template.sendBody("direct:prototypeWithOptions", "A");
        template.sendBody("direct:prototypeWithOptions", "B");

        assertMockEndpointsSatisfied();

        assertEquals(2, MyCountingBean.getInstanceCount() - before,
                "Prototype scope with bean options should create a new bean per exchange");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:singleton")
                        .to("class:org.apache.camel.component.bean.MyCountingBean")
                        .to("mock:result");

                from("direct:prototype")
                        .to("class:org.apache.camel.component.bean.MyCountingBean?scope=Prototype")
                        .to("mock:result");

                from("direct:singletonWithOptions")
                        .to("class:org.apache.camel.component.bean.MyCountingBean?bean.prefix=Bye")
                        .to("mock:result");

                from("direct:prototypeWithOptions")
                        .to("class:org.apache.camel.component.bean.MyCountingBean?scope=Prototype&bean.prefix=Bye")
                        .to("mock:result");
            }
        };
    }
}
