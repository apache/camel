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
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test for CAMEL-23282: Body OGNL expressions like ${body.name} must use class-based BeanInfo caching, not
 * instance-based, even when the body class has methods with Exchange/Message parameters or handler annotations.
 */
public class BeanInfoCacheBodyOgnlTest extends ContextTestSupport {

    @Test
    public void testBodyOgnlCacheUsesClassKey() throws Exception {
        BeanComponent bean = context.getComponent("bean", BeanComponent.class);

        assertEquals(0, bean.getCurrentBeanCacheSize());

        getMockEndpoint("mock:result").expectedMessageCount(5);

        for (int i = 0; i < 5; i++) {
            template.sendBody("direct:start", new MyOrder("order-" + i));
        }

        assertMockEndpointsSatisfied();

        // cache should have 1 entry (class-based), not 5 (instance-based)
        assertEquals(1, bean.getCurrentBeanCacheSize());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .setHeader("orderName", simple("${body.name}"))
                        .to("mock:result");
            }
        };
    }

    /**
     * Body class with a method accepting Exchange — this previously triggered instance-based BeanInfo caching.
     */
    public static class MyOrder {
        private final String name;

        public MyOrder(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void process(Exchange exchange) {
            // triggers hasCustomAnnotation=true in BeanInfo introspection
        }
    }
}
