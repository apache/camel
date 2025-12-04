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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.TabularData;

import org.apache.camel.support.task.BackgroundTask;
import org.apache.camel.support.task.Tasks;
import org.apache.camel.support.task.budget.Budgets;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

@DisabledOnOs(OS.AIX)
public class ManagedTaskRegistryTest extends ManagementTestSupport {

    @Test
    public void testManageTaskRegistry() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=services,*"), null);
        List<ObjectName> list = new ArrayList<>(set);
        ObjectName on = null;
        for (ObjectName name : list) {
            if (name.getCanonicalName().contains("DefaultTaskManagerRegistry")) {
                on = name;
                break;
            }
        }
        assertNotNull(on, "Should have found DefaultTaskManagerRegistry");

        Integer size = (Integer) mbeanServer.getAttribute(on, "Size");
        assertEquals(0, size);

        BackgroundTask task = Tasks.backgroundTask()
                .withScheduledExecutor(Executors.newSingleThreadScheduledExecutor())
                .withBudget(Budgets.timeBudget()
                        .withInterval(Duration.ofMillis(100))
                        .withMaxDuration(Duration.ofSeconds(5))
                        .build())
                .build();

        task.schedule(context, () -> false);

        final ObjectName oon = on;
        Awaitility.await().atMost(2000, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            Integer size2 = (Integer) mbeanServer.getAttribute(oon, "Size");
            assertEquals(1, size2);

            TabularData data = (TabularData) mbeanServer.invoke(oon, "listTasks", null, null);
            assertEquals(1, data.size());
        });
    }
}
