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
package org.apache.camel.builder.endpoint;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.seda.SedaConsumerNotAvailableException;
import org.apache.camel.spi.ContextReloadStrategy;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.PropertiesSource;
import org.apache.camel.support.DefaultContextReloadStrategy;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

public class CamelContextReloadStrategyTest extends CamelTestSupport {

    @Test
    public void testContextReload() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        template.sendBody("direct:start", "Hello World");
        mock.assertIsSatisfied();

        // simulate that an external system forces reload
        // where we reload the context
        ContextReloadStrategy crs = context.hasService(ContextReloadStrategy.class);
        Assertions.assertNotNull(crs);
        crs.onReload("CamelContextReloadStrategyTest");

        // need to re-get endpoint after reload
        mock = getMockEndpoint("mock:result");

        mock.reset();
        mock.expectedMessageCount(0);
        try {
            template.sendBody("direct:start", "Bye World");
            fail("Should throw exception");
        } catch (Exception e) {
            Assertions.assertInstanceOf(SedaConsumerNotAvailableException.class, e.getCause());
        }
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
        return new EndpointRouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to(seda("{{cheese}}").failIfNoConsumers(property("myfail")));

                from(seda("foo1")).to("mock:result");
            }
        };
    }

    private class MySource extends ServiceSupport implements PropertiesSource {

        private int counter;

        @Override
        public String getName() {
            return "my";
        }

        @Override
        public String getProperty(String name) {
            if ("cheese".equals(name)) {
                return "foo" + counter;
            }
            if ("myfail".equals(name)) {
                return counter == 1 ? "false" : "true";
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
