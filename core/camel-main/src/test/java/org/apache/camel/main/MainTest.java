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
package org.apache.camel.main;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ManagementStrategy;
import org.junit.jupiter.api.Test;

import static org.apache.camel.util.CollectionHelper.propertiesOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MainTest {

    @Test
    public void testMain() throws Exception {
        // lets make a simple route
        Main main = new Main();
        main.configure().addRoutesBuilder(new MyRouteBuilder());
        main.enableTrace();
        main.bind("foo", 31);
        main.start();

        CamelContext camelContext = main.getCamelContext();

        assertNotNull(camelContext);
        assertEquals(31, camelContext.getRegistry().lookupByName("foo"), "Could not find the registry bound object");

        MockEndpoint endpoint = camelContext.getEndpoint("mock:results", MockEndpoint.class);
        endpoint.expectedMinimumMessageCount(1);

        main.getCamelTemplate().sendBody("direct:start", "<message>1</message>");

        endpoint.assertIsSatisfied();

        main.stop();
    }

    @Test
    public void testTraceStandby() throws Exception {
        // lets make a simple route
        Main main = new Main();
        main.configure().addRoutesBuilder(new MyRouteBuilder());
        main.enableTraceStandby();
        main.bind("foo", 31);
        main.start();

        CamelContext camelContext = main.getCamelContext();

        assertNotNull(camelContext);
        assertEquals(31, camelContext.getRegistry().lookupByName("foo"), "Could not find the registry bound object");

        MockEndpoint endpoint = camelContext.getEndpoint("mock:results", MockEndpoint.class);
        endpoint.expectedMinimumMessageCount(1);

        main.getCamelTemplate().sendBody("direct:start", "<message>1</message>");

        endpoint.assertIsSatisfied();

        main.stop();
    }

    @Test
    public void testDisableHangupSupport() throws Exception {
        // lets make a simple route
        Main main = new Main();

        DefaultMainShutdownStrategy shutdownStrategy = new DefaultMainShutdownStrategy(main);
        shutdownStrategy.disableHangupSupport();

        main.setShutdownStrategy(shutdownStrategy);
        main.configure().addRoutesBuilder(new MyRouteBuilder());
        main.enableTrace();
        main.bind("foo", 31);
        main.start();

        CamelContext camelContext = main.getCamelContext();

        assertEquals(31, camelContext.getRegistry().lookupByName("foo"), "Could not find the registry bound object");

        MockEndpoint endpoint = camelContext.getEndpoint("mock:results", MockEndpoint.class);
        endpoint.expectedMinimumMessageCount(1);

        main.getCamelTemplate().sendBody("direct:start", "<message>1</message>");

        endpoint.assertIsSatisfied();

        main.stop();
    }

    @Test
    public void testLoadingRouteFromCommand() throws Exception {
        Main main = new Main();
        // let the main load the MyRouteBuilder
        main.parseArguments(new String[] { "-r", "org.apache.camel.main.MainTest$MyRouteBuilder" });
        main.start();

        CamelContext camelContext = main.getCamelContext();

        MockEndpoint endpoint = camelContext.getEndpoint("mock:results", MockEndpoint.class);
        endpoint.expectedMinimumMessageCount(1);

        main.getCamelTemplate().sendBody("direct:start", "<message>1</message>");

        endpoint.assertIsSatisfied();
        main.stop();
    }

    @Test
    public void testOptionalProperties() throws Exception {
        // lets make a simple route
        Main main = new Main();
        main.configure().addRoutesBuilder(new MyRouteBuilder());
        main.start();

        CamelContext camelContext = main.getCamelContext();
        // should load application.properties from classpath
        assertEquals("World", camelContext.resolvePropertyPlaceholders("{{hello}}"));

        main.stop();
    }

    @Test
    public void testDisableTracing() throws Exception {
        Main main = new Main();
        main.configure().addRoutesBuilder(new MyRouteBuilder());
        main.start();

        CamelContext camelContext = main.getCamelContext();
        assertFalse(camelContext.isTracing(), "Tracing should be disabled");

        main.stop();
    }

    @Test
    public void testLifecycleConfiguration() throws Exception {
        AtomicInteger durationMaxMessages = new AtomicInteger();

        Main main = new Main() {
            @Override
            protected void configureLifecycle(CamelContext camelContext) throws Exception {
                durationMaxMessages.set(configure().getDurationMaxMessages());
                super.configureLifecycle(camelContext);
            }
        };

        main.setOverrideProperties(propertiesOf("camel.main.duration-max-messages", "1"));
        main.start();

        CamelContext camelContext = main.getCamelContext();
        ManagementStrategy strategy = camelContext.getManagementStrategy();

        assertEquals(1, durationMaxMessages.get(), "DurationMaxMessages should be set to 1");
        assertTrue(strategy.getEventNotifiers().stream().anyMatch(n -> n instanceof MainDurationEventNotifier));

        main.stop();
    }

    @Test
    public void testDurationIdleSeconds() throws Exception {
        Main main = new Main();
        main.configure().addRoutesBuilder(new MyRouteBuilder());
        main.configure().withDurationMaxIdleSeconds(2);
        main.run();
    }

    @Test
    public void testDurationMaxSeconds() throws Exception {
        Main main = new Main();
        main.configure().addRoutesBuilder(new MyRouteBuilder());
        main.configure().withDurationMaxSeconds(2);
        main.run();
    }

    public static class MyRouteBuilder extends RouteBuilder {
        @Override
        public void configure() throws Exception {
            from("direct:start").to("mock:results");
        }
    }
}
