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
package org.apache.camel.impl;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ContextReloadStrategy;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.PropertiesSource;
import org.apache.camel.support.DefaultContextReloadStrategy;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CamelContextReloadStrategyTest extends ContextTestSupport {

    @Test
    public void testContextReload() throws Exception {
        Assertions.assertEquals("Hello 1", context.resolvePropertyPlaceholders("{{hello}}"));

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello 1", "Hello 1");
        template.sendBody("direct:start", "Camel");
        template.sendBody("direct:start", "Dog");
        mock.assertIsSatisfied();

        // simulate that an external system forces reload
        // where we reload the context
        ContextReloadStrategy crs = context.hasService(ContextReloadStrategy.class);
        Assertions.assertNotNull(crs);
        crs.onReload("CamelContextReloadStrategyTest");

        Assertions.assertEquals("Hello 2", context.resolvePropertyPlaceholders("{{hello}}"));

        // need to re-get endpoint after reload
        mock = getMockEndpoint("mock:result");

        mock.reset();
        mock.expectedBodiesReceived("Hello 2", "Hello 2");
        template.sendBody("direct:start", "World");
        template.sendBody("direct:start", "Moon");
        mock.assertIsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        PropertiesComponent pc = context.getPropertiesComponent();
        MySource my = new MySource();
        ServiceHelper.startService(my);
        pc.addPropertiesSource(my);

        ContextReloadStrategy crs = new DefaultContextReloadStrategy();
        context.addService(crs);

        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .setBody(constant("{{hello}}"))
                        .to("mock:result");
            }
        };
    }

    private static class MySource extends ServiceSupport implements PropertiesSource {

        private int counter;

        @Override
        public String getName() {
            return "my";
        }

        @Override
        public String getProperty(String name) {
            if ("hello".equals(name)) {
                return "Hello " + counter;
            }
            return null;
        }

        @Override
        protected void doStart() throws Exception {
            // the properties source will be restarted
            counter++;
        }
    }
}
