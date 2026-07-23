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
package org.apache.camel.impl.console;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.Locale;
import java.util.Map;

import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.util.UnitUtils.printUnitFromBytesDot;

@DevConsole(name = "memory", displayName = "JVM Memory", description = "Displays JVM memory information")
@Configurer(extended = true)
public class MemoryDevConsole extends AbstractDevConsole {

    public MemoryDevConsole() {
        super("jvm", "memory", "JVM Memory", "Displays JVM memory information");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        MemoryMXBean mb = ManagementFactory.getMemoryMXBean();
        if (mb != null) {
            sb.append(String.format("Heap Init: %s%n", printUnitFromBytesDot(mb.getHeapMemoryUsage().getInit())));
            sb.append(String.format("Heap Max: %s%n", printUnitFromBytesDot(mb.getHeapMemoryUsage().getMax())));
            sb.append(String.format("Heap Used: %s%n", printUnitFromBytesDot(mb.getHeapMemoryUsage().getUsed())));
            sb.append(String.format("Heap Committed: %s%n", printUnitFromBytesDot(mb.getHeapMemoryUsage().getCommitted())));
            sb.append("\n");
            sb.append(String.format("Non-Heap Init: %s%n", printUnitFromBytesDot(mb.getNonHeapMemoryUsage().getInit())));
            sb.append(String.format("Non-Heap Max: %s%n", printUnitFromBytesDot(mb.getNonHeapMemoryUsage().getMax())));
            sb.append(String.format("Non-Heap Used: %s%n", printUnitFromBytesDot(mb.getNonHeapMemoryUsage().getUsed())));
            sb.append(String.format("Non-Heap Committed: %s%n",
                    printUnitFromBytesDot(mb.getNonHeapMemoryUsage().getCommitted())));
        }

        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            String name = pool.getName().toLowerCase(Locale.ROOT);
            if ((pool.getType() == MemoryType.HEAP && (name.contains("old") || name.contains("tenured")))
                    || name.contains("metaspace")) {
                sb.append("\n");
                sb.append(String.format("%s Used: %s%n", pool.getName(), printUnitFromBytesDot(pool.getUsage().getUsed())));
                sb.append(String.format("%s Committed: %s%n", pool.getName(),
                        printUnitFromBytesDot(pool.getUsage().getCommitted())));
                long max = pool.getUsage().getMax();
                sb.append(String.format("%s Max: %s%n", pool.getName(),
                        max > 0 ? printUnitFromBytesDot(max) : "-"));
            }
        }

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        MemoryMXBean mb = ManagementFactory.getMemoryMXBean();
        if (mb != null) {
            root.put("heapMemoryInit", printUnitFromBytesDot(mb.getHeapMemoryUsage().getInit()));
            root.put("heapMemoryMax", printUnitFromBytesDot(mb.getHeapMemoryUsage().getMax()));
            root.put("heapMemoryUsed", printUnitFromBytesDot(mb.getHeapMemoryUsage().getUsed()));
            root.put("heapMemoryCommitted", printUnitFromBytesDot(mb.getHeapMemoryUsage().getCommitted()));
            root.put("nonHeapMemoryInit", printUnitFromBytesDot(mb.getNonHeapMemoryUsage().getInit()));
            root.put("nonHeapMemoryMax", printUnitFromBytesDot(mb.getNonHeapMemoryUsage().getMax()));
            root.put("nonHeapMemoryUsed", printUnitFromBytesDot(mb.getNonHeapMemoryUsage().getUsed()));
            root.put("nonHeapMemoryCommitted", printUnitFromBytesDot(mb.getNonHeapMemoryUsage().getCommitted()));
        }

        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            String name = pool.getName().toLowerCase(Locale.ROOT);
            if (pool.getType() == MemoryType.HEAP && (name.contains("old") || name.contains("tenured"))) {
                root.put("oldGenUsed", printUnitFromBytesDot(pool.getUsage().getUsed()));
                root.put("oldGenCommitted", printUnitFromBytesDot(pool.getUsage().getCommitted()));
                long max = pool.getUsage().getMax();
                root.put("oldGenMax", max > 0 ? printUnitFromBytesDot(max) : "-");
            } else if (name.contains("metaspace")) {
                root.put("metaspaceUsed", printUnitFromBytesDot(pool.getUsage().getUsed()));
                root.put("metaspaceCommitted", printUnitFromBytesDot(pool.getUsage().getCommitted()));
                long max = pool.getUsage().getMax();
                root.put("metaspaceMax", max > 0 ? printUnitFromBytesDot(max) : "-");
            }
        }

        return root;
    }
}
