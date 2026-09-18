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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonRecordSupport;

import static org.apache.camel.util.UnitUtils.printUnitFromBytesDot;

@DevConsole(name = "memory", displayName = "JVM Memory", description = "Displays JVM memory information")
@Configurer(extended = true)
public class MemoryDevConsole extends AbstractDevConsole {

    public record Response(
            @Metadata(description = "Heap memory initial size") String heapMemoryInit,
            @Metadata(description = "Heap memory max size") String heapMemoryMax,
            @Metadata(description = "Heap memory used") String heapMemoryUsed,
            @Metadata(description = "Heap memory committed") String heapMemoryCommitted,
            @Metadata(description = "Non-heap memory initial size") String nonHeapMemoryInit,
            @Metadata(description = "Non-heap memory max size") String nonHeapMemoryMax,
            @Metadata(description = "Non-heap memory used") String nonHeapMemoryUsed,
            @Metadata(description = "Non-heap memory committed") String nonHeapMemoryCommitted,
            @Metadata(description = "Old generation memory pool used (only present when such a pool exists)") String oldGenUsed,
            @Metadata(description = "Old generation memory pool committed (only present when such a pool exists)") String oldGenCommitted,
            @Metadata(description = "Old generation memory pool max (only present when such a pool exists)") String oldGenMax,
            @Metadata(description = "Metaspace memory pool used (only present when such a pool exists)") String metaspaceUsed,
            @Metadata(description = "Metaspace memory pool committed (only present when such a pool exists)") String metaspaceCommitted,
            @Metadata(description = "Metaspace memory pool max (only present when such a pool exists)") String metaspaceMax) {
    }

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
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        String heapMemoryInit = null;
        String heapMemoryMax = null;
        String heapMemoryUsed = null;
        String heapMemoryCommitted = null;
        String nonHeapMemoryInit = null;
        String nonHeapMemoryMax = null;
        String nonHeapMemoryUsed = null;
        String nonHeapMemoryCommitted = null;

        MemoryMXBean mb = ManagementFactory.getMemoryMXBean();
        if (mb != null) {
            heapMemoryInit = printUnitFromBytesDot(mb.getHeapMemoryUsage().getInit());
            heapMemoryMax = printUnitFromBytesDot(mb.getHeapMemoryUsage().getMax());
            heapMemoryUsed = printUnitFromBytesDot(mb.getHeapMemoryUsage().getUsed());
            heapMemoryCommitted = printUnitFromBytesDot(mb.getHeapMemoryUsage().getCommitted());
            nonHeapMemoryInit = printUnitFromBytesDot(mb.getNonHeapMemoryUsage().getInit());
            nonHeapMemoryMax = printUnitFromBytesDot(mb.getNonHeapMemoryUsage().getMax());
            nonHeapMemoryUsed = printUnitFromBytesDot(mb.getNonHeapMemoryUsage().getUsed());
            nonHeapMemoryCommitted = printUnitFromBytesDot(mb.getNonHeapMemoryUsage().getCommitted());
        }

        String oldGenUsed = null;
        String oldGenCommitted = null;
        String oldGenMax = null;
        String metaspaceUsed = null;
        String metaspaceCommitted = null;
        String metaspaceMax = null;

        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            String name = pool.getName().toLowerCase(Locale.ROOT);
            if (pool.getType() == MemoryType.HEAP && (name.contains("old") || name.contains("tenured"))) {
                oldGenUsed = printUnitFromBytesDot(pool.getUsage().getUsed());
                oldGenCommitted = printUnitFromBytesDot(pool.getUsage().getCommitted());
                long max = pool.getUsage().getMax();
                oldGenMax = max > 0 ? printUnitFromBytesDot(max) : "-";
            } else if (name.contains("metaspace")) {
                metaspaceUsed = printUnitFromBytesDot(pool.getUsage().getUsed());
                metaspaceCommitted = printUnitFromBytesDot(pool.getUsage().getCommitted());
                long max = pool.getUsage().getMax();
                metaspaceMax = max > 0 ? printUnitFromBytesDot(max) : "-";
            }
        }

        Response response = new Response(
                heapMemoryInit, heapMemoryMax, heapMemoryUsed, heapMemoryCommitted, nonHeapMemoryInit, nonHeapMemoryMax,
                nonHeapMemoryUsed, nonHeapMemoryCommitted, oldGenUsed, oldGenCommitted, oldGenMax, metaspaceUsed,
                metaspaceCommitted, metaspaceMax);
        return JsonRecordSupport.toJsonObject(response);
    }
}
