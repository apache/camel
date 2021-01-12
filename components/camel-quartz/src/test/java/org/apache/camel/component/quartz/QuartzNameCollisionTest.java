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
package org.apache.camel.component.quartz;

import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Check for duplicate name/group collision.
 */
public class QuartzNameCollisionTest {
    private DefaultCamelContext camel1;
    private DefaultCamelContext camel2;

    @Test
    public void testDupeName() throws Exception {
        camel1 = new DefaultCamelContext();
        camel1.setName("camel-1");
        camel1.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("quartz://myGroup/myTimerName?cron=0/1+*+*+*+*+?").to("log:one", "mock:one");
            }
        });
        camel1.start();

        RouteBuilder routeBuilder = new RouteBuilder() {
            @Override
            public void configure() {
                from("quartz://myGroup/myTimerName?cron=0/2+*+*+*+*+?").to("log:two", "mock:two");
            }
        };

        Exception ex = assertThrows(FailedToCreateRouteException.class, () -> camel1.addRoutes(routeBuilder));
        String reason = ex.getMessage();
        assertTrue(reason.contains("Trigger key myGroup.myTimerName is already in use"));
    }

    @Test
    public void testDupeNameMultiContext() throws Exception {
        camel1 = new DefaultCamelContext();
        camel1.setName("camel-1");
        camel1.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("quartz://myGroup/myTimerName?cron=0/1+*+*+*+*+?").to("log:one", "mock:one");
            }
        });
        camel1.start();

        camel2 = new DefaultCamelContext();
        camel2.setName("camel-2");
        camel2.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("quartz://myGroup/myTimerName=0/2+*+*+*+*+?").to("log:two", "mock:two");
            }
        });
        camel2.start();
    }

    /**
     * Don't check for a name collision if the job is stateful.
     */
    @Test
    public void testNoStatefulCollisionError() throws Exception {
        camel1 = new DefaultCamelContext();
        camel1.setName("camel-1");
        camel1.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("quartz://myGroup/myTimerName?stateful=true&cron=0/1+*+*+*+*+?").to("log:one", "mock:one");
            }
        });
        camel1.start();

        camel2 = new DefaultCamelContext();
        camel2.setName("camel-2");
        camel2.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("quartz://myGroup/myTimerName?stateful=true").to("log:two", "mock:two");
            }
        });
        camel2.start();
        // if no exception is thrown then this test passed.
    }

    /**
     * Make sure a resume doesn't trigger a dupe name error.
     */
    @Test
    public void testRestart() throws Exception {
        DefaultCamelContext camel = new DefaultCamelContext();

        camel.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("quartz://myGroup/myTimerName?cron=0/1+*+*+*+*+?").to("log:one", "mock:one");
            }
        });

        // traverse a litany of states
        camel.start();
        Thread.sleep(100);
        camel.suspend();
        Thread.sleep(100);
        camel.resume();
        Thread.sleep(100);
        camel.stop();
        Thread.sleep(100);
        camel.start();
        Thread.sleep(100);
        camel.stop();
    }

    /**
     * Confirm the quartz trigger is removed on route stop.
     */
    @Test
    public void testRemoveJob() throws Exception {
        camel1 = new DefaultCamelContext();
        camel1.setName("camel-1");
        camel1.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("quartz://myGroup/myTimerName?cron=0/1+*+*+*+*+?").id("route-1").to("log:one", "mock:one");
            }
        });

        camel1.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("quartz://myGroup2/myTimerName?cron=0/1+*+*+*+*+?").id("route-2").to("log:one", "mock:one");
            }
        });

        camel1.start();

        QuartzComponent component = (QuartzComponent) camel1.getComponent("quartz");
        Scheduler scheduler = component.getScheduler();
        TriggerKey triggerKey = TriggerKey.triggerKey("myTimerName", "myGroup");
        Trigger trigger = scheduler.getTrigger(triggerKey);
        assertNotNull(trigger);

        camel1.getRouteController().stopRoute("route-1");

        Trigger.TriggerState triggerState = component.getScheduler().getTriggerState(triggerKey);
        assertNotNull(trigger);
        assertEquals(Trigger.TriggerState.PAUSED, triggerState);
    }

    @AfterEach
    public void cleanUp() throws Exception {
        if (camel1 != null) {
            camel1.stop();
            camel1 = null;
        }

        if (camel2 != null) {
            camel2.stop();
            camel2 = null;
        }
    }

}
