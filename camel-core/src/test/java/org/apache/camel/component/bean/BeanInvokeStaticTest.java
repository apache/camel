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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class BeanInvokeStaticTest extends ContextTestSupport {

    public void testA() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:a").bean(MyStaticClass.class, "changeSomething").to("mock:a");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:a");
        mock.expectedBodiesReceived("Bye World");

        template.sendBody("direct:a", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testB() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:a").bean(MyStaticClass.class, "doSomething").to("mock:a");
            }
        });
        try {
            context.start();
            fail("Should have thrown exception");
        } catch (FailedToCreateRouteException e) {
            assertIsInstanceOf(RuntimeCamelException.class, e.getCause());
            assertIsInstanceOf(MethodNotFoundException.class, e.getCause().getCause());
            assertEquals("Static method with name: doSomething not found on class: org.apache.camel.component.bean.MyStaticClass", e.getCause().getCause().getMessage());
        }
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

}