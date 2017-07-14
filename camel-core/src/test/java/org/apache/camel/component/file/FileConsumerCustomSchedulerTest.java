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
package org.apache.camel.component.file;

import java.util.Timer;
import java.util.TimerTask;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.spi.ScheduledPollConsumerScheduler;

public class FileConsumerCustomSchedulerTest extends ContextTestSupport {

    private MyScheduler scheduler = new MyScheduler();

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myScheduler", scheduler);
        return jndi;
    }

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/file/custom");
        super.setUp();
    }

    public void testCustomScheduler() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBodyAndHeader("file:target/file/custom", "Hello World", Exchange.FILE_NAME, "hello.txt");

        context.startRoute("foo");

        assertMockEndpointsSatisfied();

        // the scheduler is only run once, and we can configure its properties
        assertEquals(1, scheduler.getCounter());
        assertEquals("bar", scheduler.getFoo());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/file/custom?scheduler=#myScheduler&scheduler.foo=bar&initialDelay=0&delay=10").routeId("foo").noAutoStartup()
                    .to("mock:result");
            }
        };
    }

    private static final class MyScheduler implements ScheduledPollConsumerScheduler {

        private CamelContext camelContext;
        private Timer timer;
        private TimerTask timerTask;
        private volatile int counter;
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
                    counter++;
                    task.run();
                }
            };
        }

        @Override
        public void unscheduleTask() {
            // noop
        }

        public int getCounter() {
            return counter;
        }

        public String getFoo() {
            return foo;
        }

        public void setFoo(String foo) {
            this.foo = foo;
        }

        @Override
        public void startScheduler() {
            timer = new Timer();
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
        public void shutdown() throws Exception {
            timerTask.cancel();
        }

        @Override
        public void start() throws Exception {
        }

        @Override
        public void stop() throws Exception {
        }
    }
}
