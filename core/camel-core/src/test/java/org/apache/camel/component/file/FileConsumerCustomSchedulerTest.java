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
package org.apache.camel.component.file;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.LongAdder;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.ScheduledPollConsumerScheduler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileConsumerCustomSchedulerTest extends ContextTestSupport {

    private final MyScheduler scheduler = new MyScheduler();

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry jndi = super.createCamelRegistry();
        jndi.bind("myScheduler", scheduler);
        return jndi;
    }

    @Test
    public void testCustomScheduler() throws Exception {
        getMockEndpoint("mock:result").expectedMinimumMessageCount(1);

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "hello.txt");

        context.getRouteController().startRoute("foo");

        assertMockEndpointsSatisfied();

        // the scheduler is only run once, and we can configure its properties
        // (camel may run the scheduler once during startup so the value is +1)
        assertTrue(scheduler.getCounter() <= 2);
        assertEquals("bar", scheduler.getFoo());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?scheduler=#myScheduler&scheduler.foo=bar&initialDelay=0&delay=10"))
                        .routeId("foo").autoStartup(false).to("mock:result");
            }
        };
    }

    private static final class MyScheduler implements ScheduledPollConsumerScheduler {

        private CamelContext camelContext;
        private TimerTask timerTask;
        private final LongAdder counter = new LongAdder();
        private String foo;

        @Override
        public void onInit(Consumer consumer) {
            // noop
        }

        @Override
        public void scheduleTask(final Runnable task) {
            this.timerTask = new TimerTask() {
                @Override
                public void run() {
                    counter.increment();
                    task.run();
                }
            };
        }

        @Override
        public void unscheduleTask() {
            // noop
        }

        public int getCounter() {
            return counter.intValue();
        }

        public String getFoo() {
            return foo;
        }

        public void setFoo(String foo) {
            this.foo = foo;
        }

        @Override
        public void startScheduler() {
            Timer timer = new Timer();
            timer.schedule(timerTask, 10);
        }

        @Override
        public boolean isSchedulerStarted() {
            return true;
        }

        @Override
        public void setCamelContext(CamelContext camelContext) {
            this.camelContext = camelContext;
        }

        @Override
        public CamelContext getCamelContext() {
            return camelContext;
        }

        @Override
        public void shutdown() {
            timerTask.cancel();
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }
    }
}
