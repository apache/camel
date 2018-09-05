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

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.SupervisingRouteController;
import org.apache.camel.spring.boot.dummy.DummyComponent;
import org.apache.camel.util.backoff.BackOffTimer;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.awaitility.Awaitility.await;

@DirtiesContext
@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
        CamelAutoConfiguration.class,
        SupervisingRouteControllerAutoConfiguration.class,
        SupervisingRouteControllerRestartTest.TestConfiguration.class
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
public class SupervisingRouteControllerRestartTest {

    @Autowired
    private CamelContext context;

    @Test
    public void testRouteRestart() throws Exception {
        Assert.assertNotNull(context.getRouteController());
        Assert.assertTrue(context.getRouteController() instanceof SupervisingRouteController);

        SupervisingRouteController controller = context.getRouteController().unwrap(SupervisingRouteController.class);

        // Wait for the controller to start the routes
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            // now its suspended by the policy
            Assert.assertEquals(ServiceStatus.Started, context.getRouteStatus("foo"));
            Assert.assertEquals(ServiceStatus.Started, context.getRouteStatus("bar"));
            Assert.assertEquals(ServiceStatus.Started, context.getRouteStatus("dummy"));
        });

        // restart the dummy route which should fail on first attempt
        controller.stopRoute("dummy");

        Assert.assertNull(context.getRoute("dummy").getRouteContext().getRouteController());

        try {
            controller.startRoute("dummy");
        } catch (Exception e) {
            Assert.assertEquals("Forced error on restart", e.getMessage());
        }

        Assert.assertTrue(controller.getBackOffContext("dummy").isPresent());
        Assert.assertEquals(BackOffTimer.Task.Status.Active, controller.getBackOffContext("dummy").get().getStatus());
        Assert.assertTrue(controller.getBackOffContext("dummy").get().getCurrentAttempts() > 0);

        // Wait for wile to give time to the controller to start the route
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            // now its suspended by the policy
            Assert.assertEquals(ServiceStatus.Started, context.getRouteStatus("dummy"));
            Assert.assertNotNull(context.getRoute("dummy").getRouteContext().getRouteController());
            Assert.assertFalse(controller.getBackOffContext("dummy").isPresent());
        });
    }

    // *************************************
    // Config
    // *************************************

    @Configuration
    public static class TestConfiguration {

        @Bean
        public RouteBuilder routeBuilder() {
            return new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    getContext().addComponent("dummy", new DummyComponent());

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

                    from("dummy:foo?failOnRestart=true")
                        .id("dummy")
                        .to("mock:dummy");
                }
            };
        }
    }
}
