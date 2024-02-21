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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeanEndpointTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("foo", new FooBean());
        return jndi;
    }

    @Test
    public void testBeanEndpointCtr() throws Exception {
        final BeanEndpoint endpoint = new BeanEndpoint();
        endpoint.setCamelContext(context);
        endpoint.setEndpointUriIfNotSpecified("bean:foo");

        endpoint.setBeanName("foo");
        assertEquals("foo", endpoint.getBeanName());

        assertTrue(endpoint.isSingleton());
        assertNull(endpoint.getBeanHolder());
        assertNull(endpoint.getMethod());
        assertEquals("bean:foo", endpoint.getEndpointUri());

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to(endpoint);
            }
        });
        context.start();

        String out = template.requestBody("direct:start", "World", String.class);
        assertEquals("Hello World", out);
    }

    @Test
    public void testBeanEndpointCtrComponent() throws Exception {
        final BeanComponent comp = context.getComponent("bean", BeanComponent.class);
        final BeanEndpoint endpoint = new BeanEndpoint("bean:foo", comp);
        endpoint.setCamelContext(context);

        endpoint.setBeanName("foo");
        assertEquals("foo", endpoint.getBeanName());

        assertTrue(endpoint.isSingleton());
        assertNull(endpoint.getBeanHolder());
        assertNull(endpoint.getMethod());
        assertEquals("bean:foo", endpoint.getEndpointUri());

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to(endpoint);
            }
        });
        context.start();

        String out = template.requestBody("direct:start", "World", String.class);
        assertEquals("Hello World", out);
    }

    @Test
    public void testBeanEndpointCtrComponentBeanProcessor() throws Exception {
        final BeanComponent comp = context.getComponent("bean", BeanComponent.class);

        BeanHolder holder = new RegistryBean(context, "foo", null, null);
        final BeanProcessor bp = new BeanProcessor(holder);
        final BeanEndpoint endpoint = new BeanEndpoint("bean:foo", comp, bp);

        endpoint.setBeanName("foo");
        assertEquals("foo", endpoint.getBeanName());

        assertTrue(endpoint.isSingleton());
        assertNull(endpoint.getBeanHolder());
        assertNull(endpoint.getMethod());
        assertEquals("bean:foo", endpoint.getEndpointUri());

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to(endpoint);
            }
        });
        context.start();

        String out = template.requestBody("direct:start", "World", String.class);
        assertEquals("Hello World", out);
    }

    @Test
    public void testBeanEndpointCtrWithMethod() throws Exception {
        final BeanEndpoint endpoint = new BeanEndpoint();
        endpoint.setCamelContext(context);

        endpoint.setBeanName("foo");
        endpoint.setMethod("hello");
        assertEquals("foo", endpoint.getBeanName());

        assertTrue(endpoint.isSingleton());
        assertNull(endpoint.getBeanHolder());
        assertEquals("hello", endpoint.getMethod());
        assertEquals("bean:foo?method=hello", endpoint.getEndpointUri());

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to(endpoint);
            }
        });
        context.start();

        String out = template.requestBody("direct:start", "World", String.class);
        assertEquals("Hello World", out);
    }

    public static class FooBean {

        public String hello(String hello) {
            return "Hello " + hello;
        }
    }
}
