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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;

public class BeanComponentMissingParenthesisTest extends ContextTestSupport {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myBean", new MyContactBean());
        return jndi;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testCorrect() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("bean:myBean?method=concat(${body}, ${header.foo})")
                    .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello=Camel");
        template.sendBodyAndHeader("direct:start", "Hello", "foo", "Camel");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testMissing() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("bean:myBean?method=concat(${body}, ${header.foo}")
                    .to("mock:result");
            }
        });
        context.start();

        try {
            template.sendBodyAndHeader("direct:start", "Hello", "foo", "Camel");
            fail("Should throw exception");
        } catch (CamelExecutionException e) {
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Method should end with parenthesis, was concat(${body}, ${header.foo}", iae.getMessage());
        }
    }

    @Test
    public void testInvalidName() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("bean:myBean?method=--concat(${body}, ${header.foo})")
                    .to("mock:result");
            }
        });
        context.start();

        try {
            template.sendBodyAndHeader("direct:start", "Hello", "foo", "Camel");
            fail("Should throw exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(MethodNotFoundException.class, e.getCause());
        }
    }

    public String doSomething(String body, String header) {
        return body + "=" + header;
    }

}
