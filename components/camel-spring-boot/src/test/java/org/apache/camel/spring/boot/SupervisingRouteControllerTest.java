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
package org.apache.camel.spring.boot;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.SupervisingRouteController;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.util.backoff.BackOff;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

@DirtiesContext
@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
        CamelAutoConfiguration.class,
        SupervisingRouteControllerAutoConfiguration.class,
        SupervisingRouteControllerTest.TestConfiguration.class
    },
    properties = {
        "camel.springboot.xml-routes = false",
        "camel.springboot.main-run-controller = true",
        "camel.supervising.controller.enabled = true",
        "camel.supervising.controller.initial-delay = 2s",
        "camel.supervising.controller.default-back-off.delay = 1s",
        "camel.supervising.controller.default-back-off.max-attempts = 10",
        "camel.supervising.controller.routes.bar.back-off.delay = 10s",
        "camel.supervising.controller.routes.bar.back-off.max-attempts = 3",
        "camel.supervising.controller.routes.timer-unmanaged.supervise = false"
    }
)
public class SupervisingRouteControllerTest {
    @Autowired
    private CamelContext context;

    @Test
    public void test() throws Exception {
        Assert.assertNotNull(context.getRouteController());
        Assert.assertTrue(context.getRouteController() instanceof SupervisingRouteController);

        SupervisingRouteController controller = context.getRouteController().unwrap(SupervisingRouteController.class);

        Assert.assertEquals(3, controller.getControlledRoutes().size());
        Assert.assertEquals(2, controller.getInitialDelay().getSeconds());

        // Route foo
        BackOff foo = controller.getBackOff("foo");
        Assert.assertEquals(1, foo.getDelay().getSeconds());
        Assert.assertEquals(Long.MAX_VALUE, foo.getMaxDelay().toMillis());
        Assert.assertEquals(10L, foo.getMaxAttempts().longValue());

        // Route bar
        BackOff bar = controller.getBackOff("bar");
        Assert.assertEquals(10, bar.getDelay().getSeconds());
        Assert.assertEquals(Long.MAX_VALUE, bar.getMaxDelay().toMillis());
        Assert.assertEquals(3L, bar.getMaxAttempts().longValue());

        Assert.assertEquals(controller, context.getRoute("foo").getRouteContext().getRouteController());
        Assert.assertEquals(controller, context.getRoute("bar").getRouteContext().getRouteController());
        Assert.assertNull(context.getRoute("timer-unmanaged").getRouteContext().getRouteController());
        Assert.assertNull(context.getRoute("timer-no-autostartup").getRouteContext().getRouteController());

        Assert.assertEquals(ServiceStatus.Stopped, context.getRouteStatus("foo"));
        Assert.assertEquals(ServiceStatus.Stopped, context.getRouteStatus("bar"));
    }

    // *************************************
    // Config
    // *************************************

    @Configuration
    public static class TestConfiguration {
        private static final int PORT = AvailablePortFinder.getNextAvailable();

        @Bean
        public RouteBuilder routeBuilder() {
            return new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("timer:foo?period=5s")
                        .id("foo")
                        .startupOrder(2)
                        .to("mock:foo");
                    from("timer:bar?period=5s")
                        .id("bar")
                        .startupOrder(1)
                        .to("mock:bar");
                    from("timer:unmanaged?period=5s")
                        .id("timer-unmanaged")
                        .to("mock:timer-unmanaged");
                    from("timer:no-autostartup?period=5s")
                        .id("timer-no-autostartup")
                        .autoStartup(false)
                        .to("mock:timer-no-autostartup");

                    fromF("jetty:http://localhost:%d", PORT)
                        .id("jetty")
                        .to("mock:jetty");
                }
            };
        }
    }
}
