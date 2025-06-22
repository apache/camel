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
package org.apache.camel.management;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.TabularData;

import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.backoff.BackOff;
import org.apache.camel.util.backoff.BackOffTimer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisabledOnOs(OS.AIX)
public class ManagedBackOffTimerTest extends ManagementTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testManageBackOffTimer() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger counter = new AtomicInteger();

        BackOffTimer timer = PluginHelper.getBackOffTimerFactory(context.getCamelContextExtension())
                .newBackOffTimer("Cheese");
        ServiceHelper.startService(timer);

        context.start();

        // get the bean introspection for the route
        MBeanServer mbeanServer = getMBeanServer();
        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=services,*"), null);
        List<ObjectName> list = new ArrayList<>(set);
        ObjectName on = null;
        for (ObjectName name : list) {
            if (name.getCanonicalName().contains("DefaultBackOffTimer")) {
                on = name;
                break;
            }
        }

        assertNotNull(on, "Should have found DefaultBackOffTimer");

        String name = (String) mbeanServer.getAttribute(on, "Name");
        assertEquals("Cheese", name);

        final BackOff backOff = BackOff.builder().delay(100).removeOnComplete(false).build();
        final AtomicLong first = new AtomicLong();

        BackOffTimer.Task task = timer.schedule(
                backOff,
                context -> {
                    assertEquals(counter.incrementAndGet(), context.getCurrentAttempts());
                    assertEquals(100, context.getCurrentDelay());
                    assertEquals(100L * counter.get(), context.getCurrentElapsedTime());
                    if (first.get() == 0) {
                        first.set(context.getFirstAttemptTime());
                    } else {
                        assertEquals(first.get(), context.getFirstAttemptTime());
                    }

                    return counter.get() < 5;
                });

        task.whenComplete(
                (context, throwable) -> {
                    assertEquals(5, counter.get());
                    latch.countDown();
                });

        latch.await(5, TimeUnit.SECONDS);

        Integer size = (Integer) mbeanServer.getAttribute(on, "Size");
        assertEquals(1, size);

        TabularData data = (TabularData) mbeanServer.invoke(on, "listTasks", null, null);
        assertEquals(1, data.size());

        ServiceHelper.stopService(timer);
    }

}
