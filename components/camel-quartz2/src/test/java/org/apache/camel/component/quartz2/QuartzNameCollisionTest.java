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
package org.apache.camel.component.quartz2;

import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerKey;

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
                from("quartz2://myGroup/myTimerName?cron=0/1+*+*+*+*+?").to("log:one", "mock:one");
            }
        });
        camel1.start();

        try {
            camel1.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("quartz2://myGroup/myTimerName?cron=0/2+*+*+*+*+?").to("log:two", "mock:two");
                }
            });
            Assert.fail("Should have thrown an exception");
        } catch (FailedToCreateRouteException e) {
            String reason = e.getMessage();
            Assert.assertEquals(reason.indexOf("Trigger key myGroup.myTimerName is already in use") >= 0, true);
        }
    }

    @Test
    public void testDupeNameMultiContext() throws Exception {
        camel1 = new DefaultCamelContext();
        camel1.setName("camel-1");
        camel1.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("quartz2://myGroup/myTimerName?cron=0/1+*+*+*+*+?").to("log:one", "mock:one");
            }
        });
        camel1.start();

        camel2 = new DefaultCamelContext();
        camel2.setName("camel-2");
        camel2.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("quartz2://myGroup/myTimerName=0/2+*+*+*+*+?").to("log:two", "mock:two");
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
                from("quartz2://myGroup/myTimerName?stateful=true&cron=0/1+*+*+*+*+?").to("log:one", "mock:one");
            }
        });
        camel1.start();

        camel2 = new DefaultCamelContext();
        camel2.setName("camel-2");
        camel2.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("quartz2://myGroup/myTimerName?stateful=true").to("log:two", "mock:two");
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
                from("quartz2://myGroup/myTimerName?cron=0/1+*+*+*+*+?").to("log:one", "mock:one");
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
                from("quartz2://myGroup/myTimerName?cron=0/1+*+*+*+*+?").id("route-1").to("log:one", "mock:one");
            }
        });

        camel1.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("quartz2://myGroup2/myTimerName?cron=0/1+*+*+*+*+?").id("route-2").to("log:one", "mock:one");
            }
        });

        camel1.start();

        QuartzComponent component = (QuartzComponent) camel1.getComponent("quartz2");
        Scheduler scheduler = component.getScheduler();
        TriggerKey triggerKey = TriggerKey.triggerKey("myTimerName", "myGroup");
        Trigger trigger = scheduler.getTrigger(triggerKey);
        Assert.assertNotNull(trigger);

        camel1.stopRoute("route-1");

        Trigger.TriggerState triggerState = component.getScheduler().getTriggerState(triggerKey);
        Assert.assertNotNull(trigger);
        Assert.assertEquals(Trigger.TriggerState.PAUSED, triggerState);
    }

    @After
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
