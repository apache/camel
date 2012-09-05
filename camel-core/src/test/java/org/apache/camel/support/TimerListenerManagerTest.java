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
package org.apache.camel.support;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.TimerListener;

/**
 *
 */
public class TimerListenerManagerTest extends ContextTestSupport {

    private final MyTask task = new MyTask();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testTimerListenerManager() throws Exception {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        TimerListenerManager manager = new TimerListenerManager();
        manager.setCamelContext(context);
        manager.addTimerListener(task);
        manager.start();

        assertTrue("Should be invoked", task.await());

        manager.stop();
        manager.removeTimerListener(task);
        executor.shutdown();
    }

    private class MyTask implements TimerListener {

        private CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void onTimer() {
            latch.countDown();
        }

        public boolean await() throws InterruptedException {
            return latch.await(5, TimeUnit.SECONDS);
        }
    }
}
