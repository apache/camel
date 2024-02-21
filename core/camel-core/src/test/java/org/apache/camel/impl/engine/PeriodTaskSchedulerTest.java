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
package org.apache.camel.impl.engine;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.spi.PeriodTaskScheduler;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.TimerListenerManager;
import org.apache.camel.support.service.ServiceSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PeriodTaskSchedulerTest extends ContextTestSupport {

    private final AtomicInteger counter = new AtomicInteger();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testScheduler() throws Exception {
        counter.set(0);

        PeriodTaskScheduler scheduler = PluginHelper.getPeriodTaskScheduler(context);
        if (scheduler instanceof TimerListenerManager) {
            // speedup unit test
            ((TimerListenerManager) scheduler).setInterval(10);
        }
        scheduler.schedulePeriodTask(counter::incrementAndGet, 10);
        context.start();

        Awaitility.waitAtMost(5, TimeUnit.SECONDS).until(() -> counter.get() > 0);
    }

    @Test
    public void testSchedulerLifecycle() throws Exception {
        counter.set(0);

        MyTask task = new MyTask();

        PeriodTaskScheduler scheduler = PluginHelper.getPeriodTaskScheduler(context);
        if (scheduler instanceof TimerListenerManager) {
            // speedup unit test
            ((TimerListenerManager) scheduler).setInterval(10);
        }
        scheduler.schedulePeriodTask(task, 10);
        context.start();

        Awaitility.waitAtMost(5, TimeUnit.SECONDS).until(() -> counter.get() > 1);

        Assertions.assertTrue(task.getEvent().startsWith("start"));
        Assertions.assertTrue(task.getEvent().endsWith("run"));

        context.stop();

        Assertions.assertTrue(task.getEvent().endsWith("stop"));
    }

    private class MyTask extends ServiceSupport implements Runnable {

        private volatile String event = "";

        public String getEvent() {
            return event;
        }

        @Override
        protected void doStart() throws Exception {
            event += "start";
        }

        @Override
        protected void doStop() throws Exception {
            event += "stop";
        }

        @Override
        public void run() {
            counter.incrementAndGet();
            event += "run";
        }
    }

}
