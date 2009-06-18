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
package org.apache.camel.component.quartz;

import org.apache.camel.builder.RouteBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.quartz.Scheduler;
import org.quartz.SimpleTrigger;
import org.quartz.impl.StdSchedulerFactory;

/**
 * @version $Revision$
 */
public class QuartzEndpointTest extends QuartzRouteTest {

    private Scheduler scheduler;

    @Override
    @Before
    public void setUp() throws Exception {
        scheduler =  StdSchedulerFactory.getDefaultScheduler();
        scheduler.start();
        super.setUp();

    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        scheduler.shutdown();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() throws Exception {
                QuartzEndpoint endpoint = new QuartzEndpoint();
                endpoint.setCamelContext(context);
                endpoint.setScheduler(scheduler);

                SimpleTrigger trigger = new SimpleTrigger();
                trigger.setGroup("myGroup");
                trigger.setName("myTimerName");
                trigger.setRepeatCount(1);
                trigger.setRepeatInterval(2);

                endpoint.setTrigger(trigger);

                context.addEndpoint("qtx", endpoint);

                from("qtx").to("mock:result");
            }
        };
    }
}