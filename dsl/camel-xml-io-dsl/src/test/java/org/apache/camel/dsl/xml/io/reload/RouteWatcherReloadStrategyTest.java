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
package org.apache.camel.dsl.xml.io.reload;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.RouteWatcherReloadStrategy;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.test.junit5.TestSupport.createDirectory;
import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RouteWatcherReloadStrategyTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(RouteWatcherReloadStrategyTest.class);

    private RouteWatcherReloadStrategy reloadStrategy;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        reloadStrategy = new RouteWatcherReloadStrategy();
        reloadStrategy.setFolder("target/dummy");
        reloadStrategy.setPattern("*.xml");
        // to make unit test faster
        reloadStrategy.setPollTimeout(100);
        context.addService(reloadStrategy);
        return context;
    }

    @Test
    public void testAddNewRoute() throws Exception {
        deleteDirectory("target/dummy");
        createDirectory("target/dummy");

        context.start();

        // there are 0 routes to begin with
        assertEquals(0, context.getRoutes().size());

        LOG.info("Copying file to target/dummy");

        // create an xml file with some routes
        FileUtil.copyFile(new File("src/test/resources/org/apache/camel/reload/barRoute.xml"),
                new File("target/dummy/barRoute.xml"));

        // wait for that file to be processed
        // (is slow on osx, so wait up till 20 seconds)
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(1, context.getRoutes().size()));

        // and the route should work
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        template.sendBody("direct:bar", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testUpdateExistingRoute() throws Exception {
        deleteDirectory("target/dummy");
        createDirectory("target/dummy");

        // the bar route is added two times, at first, and then when updated
        final CountDownLatch latch = new CountDownLatch(2);
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) throws Exception {
                latch.countDown();
            }

            @Override
            public boolean isEnabled(CamelEvent event) {
                return event instanceof CamelEvent.RouteAddedEvent;
            }
        });

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:bar").routeId("bar").to("mock:foo");
            }
        });

        context.start();

        assertEquals(1, context.getRoutes().size());

        // and the route should work sending to mock:foo
        getMockEndpoint("mock:bar").expectedMessageCount(0);
        getMockEndpoint("mock:foo").expectedMessageCount(1);
        template.sendBody("direct:bar", "Hello World");
        MockEndpoint.assertIsSatisfied(context);

        MockEndpoint.resetMocks(context);

        LOG.info("Copying file to target/dummy");

        // create an xml file with some routes
        FileUtil.copyFile(new File("src/test/resources/org/apache/camel/reload/barRoute.xml"),
                new File("target/dummy/barRoute.xml"));

        // wait for that file to be processed and remove/add the route
        // (is slow on osx, so wait up till 20 seconds)
        boolean done = latch.await(20, TimeUnit.SECONDS);
        assertTrue(done, "Should reload file within 20 seconds");

        // and the route should be changed to route to mock:bar instead of mock:foo
        Thread.sleep(500);
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:foo").expectedMessageCount(0);
        template.sendBody("direct:bar", "Bye World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testUpdateXmlRoute() throws Exception {
        deleteDirectory("target/dummy");
        createDirectory("target/dummy");

        // the bar route is added two times, at first, and then when updated
        final CountDownLatch latch = new CountDownLatch(2);
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) throws Exception {
                latch.countDown();
            }

            @Override
            public boolean isEnabled(CamelEvent event) {
                return event instanceof CamelEvent.RouteAddedEvent;
            }
        });

        context.start();

        // there are 0 routes to begin with
        assertEquals(0, context.getRoutes().size());

        LOG.info("Copying file to target/dummy");

        // create an xml file with some routes
        FileUtil.copyFile(new File("src/test/resources/org/apache/camel/reload/barRoute.xml"),
                new File("target/dummy/barRoute.xml"));

        // wait for that file to be processed
        // (is slow on osx, so wait up till 20 seconds)
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(1, context.getRoutes().size()));

        // and the route should work
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        template.sendBody("direct:bar", "Hello World");
        MockEndpoint.assertIsSatisfied(context);

        MockEndpoint.resetMocks(context);

        // now update the file
        LOG.info("Updating file in target/dummy");

        // create an xml file with some routes
        FileUtil.copyFile(new File("src/test/resources/org/apache/camel/reload/barUpdatedRoute.xml"),
                new File("target/dummy/barRoute.xml"));

        // wait for that file to be processed and remove/add the route
        // (is slow on osx, so wait up till 20 seconds)
        boolean done = latch.await(20, TimeUnit.SECONDS);
        assertTrue(done, "Should reload file within 20 seconds");

        // and the route should work with the update
        Thread.sleep(500);
        getMockEndpoint("mock:bar").expectedBodiesReceived("Bye Camel");
        template.sendBody("direct:bar", "Camel");
        MockEndpoint.assertIsSatisfied(context);
    }
}
