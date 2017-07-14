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
package org.apache.camel.processor;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.model.Constants;
import org.apache.camel.spi.MaskingFormatter;
import org.apache.camel.util.jndi.JndiTest;
import org.junit.Assert;
import org.junit.Test;

public class LogEipMaskTest {

    protected JndiRegistry registry;

    protected CamelContext createCamelContext() throws Exception {
        registry = new JndiRegistry(JndiTest.createInitialContext());
        CamelContext context = new DefaultCamelContext(registry);
        context.addRoutes(createRouteBuilder());
        return context;
    }

    @Test
    public void testLogEipMask() throws Exception {
        CamelContext context = createCamelContext();
        MockEndpoint mock = context.getEndpoint("mock:foo", MockEndpoint.class);
        mock.expectedMessageCount(1);
        context.setLogMask(true);
        context.start();
        context.createProducerTemplate().sendBody("direct:foo", "mask password=\"my passw0rd!\"");
        context.createProducerTemplate().sendBody("direct:noMask", "no-mask password=\"my passw0rd!\"");
        mock.assertIsSatisfied();
        context.stop();
    }

    @Test
    public void testCustomFormatter() throws Exception {
        CamelContext context = createCamelContext();
        MockMaskingFormatter customFormatter = new MockMaskingFormatter();
        registry.bind(Constants.CUSTOM_LOG_MASK_REF, customFormatter);
        context.setLogMask(true);
        context.start();
        context.createProducerTemplate().sendBody("direct:foo", "mock password=\"my passw0rd!\"");
        Assert.assertEquals("Got mock password=\"my passw0rd!\"", customFormatter.received);
        context.stop();
    }

    public static class MockMaskingFormatter implements MaskingFormatter {
        private String received;
        @Override
        public String format(String source) {
            received = source;
            return source;
        }
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").routeId("foo").log("Got ${body}").to("mock:foo");
                from("direct:noMask").routeId("noMask").logMask("false").log("Got ${body}").to("mock:noMask");
            }
        };
    }

}
