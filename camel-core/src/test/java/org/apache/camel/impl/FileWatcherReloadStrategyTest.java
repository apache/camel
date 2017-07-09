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
package org.apache.camel.impl;

import java.io.File;
import java.util.EventObject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.management.event.RouteAddedEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.util.FileUtil;

import static org.awaitility.Awaitility.await;

public class FileWatcherReloadStrategyTest extends ContextTestSupport {

    private FileWatcherReloadStrategy reloadStrategy;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        reloadStrategy = new FileWatcherReloadStrategy();
        reloadStrategy.setFolder("target/dummy");
        // to make unit test faster
        reloadStrategy.setPollTimeout(100);
        context.setReloadStrategy(reloadStrategy);
        return context;
    }

    public void testAddNewRoute() throws Exception {
        deleteDirectory("target/dummy");
        createDirectory("target/dummy");

        context.start();

        // there are 0 routes to begin with
        assertEquals(0, context.getRoutes().size());

        log.info("Copying file to target/dummy");

        // create an xml file with some routes
        FileUtil.copyFile(new File("src/test/resources/org/apache/camel/model/barRoute.xml"), new File("target/dummy/barRoute.xml"));

        // wait for that file to be processed
        // (is slow on osx, so wait up till 20 seconds)
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(1, context.getRoutes().size()));

        // and the route should work
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        template.sendBody("direct:bar", "Hello World");
        assertMockEndpointsSatisfied();
    }

    public void testUpdateExistingRoute() throws Exception {
        deleteDirectory("target/dummy");
        createDirectory("target/dummy");

        // the bar route is added two times, at first, and then when updated
        final CountDownLatch latch = new CountDownLatch(2);
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(EventObject event) throws Exception {
                latch.countDown();
            }

            @Override
            public boolean isEnabled(EventObject event) {
                return event instanceof RouteAddedEvent;
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
        assertMockEndpointsSatisfied();

        resetMocks();

        log.info("Copying file to target/dummy");

        // create an xml file with some routes
        FileUtil.copyFile(new File("src/test/resources/org/apache/camel/model/barRoute.xml"), new File("target/dummy/barRoute.xml"));

        // wait for that file to be processed and remove/add the route
        // (is slow on osx, so wait up till 20 seconds)
        boolean done = latch.await(20, TimeUnit.SECONDS);
        assertTrue("Should reload file within 20 seconds", done);

        // and the route should be changed to route to mock:bar instead of mock:foo
        Thread.sleep(500);
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:foo").expectedMessageCount(0);
        template.sendBody("direct:bar", "Bye World");
        assertMockEndpointsSatisfied();
    }

    public void testUpdateXmlRoute() throws Exception {
        deleteDirectory("target/dummy");
        createDirectory("target/dummy");

        // the bar route is added two times, at first, and then when updated
        final CountDownLatch latch = new CountDownLatch(2);
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(EventObject event) throws Exception {
                latch.countDown();
            }

            @Override
            public boolean isEnabled(EventObject event) {
                return event instanceof RouteAddedEvent;
            }
        });

        context.start();

        // there are 0 routes to begin with
        assertEquals(0, context.getRoutes().size());

        log.info("Copying file to target/dummy");

        // create an xml file with some routes
        FileUtil.copyFile(new File("src/test/resources/org/apache/camel/model/barRoute.xml"), new File("target/dummy/barRoute.xml"));

        // wait for that file to be processed
        // (is slow on osx, so wait up till 20 seconds)
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(1, context.getRoutes().size()));

        // and the route should work
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        template.sendBody("direct:bar", "Hello World");
        assertMockEndpointsSatisfied();

        resetMocks();

        // now update the file
        log.info("Updating file in target/dummy");

        // create an xml file with some routes
        FileUtil.copyFile(new File("src/test/resources/org/apache/camel/model/barUpdatedRoute.xml"), new File("target/dummy/barRoute.xml"));

        // wait for that file to be processed and remove/add the route
        // (is slow on osx, so wait up till 20 seconds)
        boolean done = latch.await(20, TimeUnit.SECONDS);
        assertTrue("Should reload file within 20 seconds", done);

        // and the route should work with the update
        Thread.sleep(500);
        getMockEndpoint("mock:bar").expectedBodiesReceived("Bye Camel");
        template.sendBody("direct:bar", "Camel");
        assertMockEndpointsSatisfied();
    }
}
