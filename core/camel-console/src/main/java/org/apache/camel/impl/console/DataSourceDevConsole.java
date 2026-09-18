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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "datasource", displayName = "DataSource", description = "Displays DataSource connection pool metrics")
@Configurer(extended = true)
public class DataSourceDevConsole extends AbstractDevConsole {

    private static final String HIKARI_CLASS = "com.zaxxer.hikari.HikariDataSource";
    private static final String AGROAL_CLASS = "io.agroal.api.AgroalDataSource";

    public record Entry(
            @Metadata(description = "The registry bean name") String name,
            @Metadata(description = "The DataSource implementation class") String type,
            @Metadata(description = "The detected connection pool type: HikariCP, Agroal, or Unknown") String poolType,
            @Metadata(description = "The pool name (HikariCP only)") String poolName,
            @Metadata(description = "The maximum pool size (only present once the pool is initialized)") Long maxPoolSize,
            @Metadata(description = "Number of active connections (only present once the pool is initialized)") Long active,
            @Metadata(description = "Number of idle connections (only present once the pool is initialized)") Long idle,
            @Metadata(description = "Total number of connections (only present once the pool is initialized)") Long total,
            @Metadata(description = "Number of threads awaiting a connection (HikariCP only)") Long waiting,
            @Metadata(description = "Maximum number of connections used at once (Agroal only)") Long maxUsed,
            @Metadata(description = "Number of leak detections (Agroal only)") Long leakDetection,
            @Metadata(description = "Number of connections created (Agroal only)") Long created) {
    }

    public record Response(@Metadata(description = "The DataSources") List<Entry> dataSources) {
    }

    public DataSourceDevConsole() {
        super("camel", "datasource", "DataSource", "Displays DataSource connection pool metrics");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        Map<String, DataSource> dataSources
                = getCamelContext().getRegistry().findByTypeWithName(DataSource.class);

        if (dataSources.isEmpty()) {
            sb.append("No DataSources found in registry\n");
            return sb.toString();
        }

        for (Map.Entry<String, DataSource> entry : dataSources.entrySet()) {
            DataSource ds = entry.getValue();
            String poolType = detectPoolType(ds);

            sb.append(String.format("DataSource: %s (%s)%n", entry.getKey(), poolType));
            sb.append(String.format("  Type: %s%n", ds.getClass().getName()));

            if ("HikariCP".equals(poolType)) {
                appendHikariText(sb, ds);
            } else if ("Agroal".equals(poolType)) {
                appendAgroalText(sb, ds);
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        List<Entry> entries = new ArrayList<>();

        Map<String, DataSource> dataSources
                = getCamelContext().getRegistry().findByTypeWithName(DataSource.class);

        for (Map.Entry<String, DataSource> entry : dataSources.entrySet()) {
            DataSource ds = entry.getValue();
            String poolType = detectPoolType(ds);

            Entry e;
            if ("HikariCP".equals(poolType)) {
                e = collectHikariMetrics(entry.getKey(), ds, poolType);
            } else if ("Agroal".equals(poolType)) {
                e = collectAgroalMetrics(entry.getKey(), ds, poolType);
            } else {
                e = new Entry(
                        entry.getKey(), ds.getClass().getName(), poolType, null, null, null, null, null, null, null,
                        null, null);
            }
            entries.add(e);
        }

        Response response = new Response(entries);
        return JsonRecordSupport.toJsonObject(response);
    }

    private static String detectPoolType(DataSource ds) {
        String className = ds.getClass().getName();
        if (HIKARI_CLASS.equals(className)) {
            return "HikariCP";
        } else if (AGROAL_CLASS.equals(className)) {
            return "Agroal";
        }
        return "Unknown";
    }

    // ---- HikariCP ----

    private Entry collectHikariMetrics(String name, DataSource ds, String poolType) {
        Object poolNameObj = invokeMethod(ds, "getPoolName");
        String poolName = poolNameObj != null ? String.valueOf(poolNameObj) : null;
        Long maxPoolSize = asLong(invokeMethod(ds, "getMaximumPoolSize"));

        Long active = null;
        Long idle = null;
        Long total = null;
        Long waiting = null;
        Object mxBean = invokeMethod(ds, "getHikariPoolMXBean");
        if (mxBean != null) {
            active = asLong(invokeMethod(mxBean, "getActiveConnections"));
            idle = asLong(invokeMethod(mxBean, "getIdleConnections"));
            total = asLong(invokeMethod(mxBean, "getTotalConnections"));
            waiting = asLong(invokeMethod(mxBean, "getThreadsAwaitingConnection"));
        }

        return new Entry(
                name, ds.getClass().getName(), poolType, poolName, maxPoolSize, active, idle, total, waiting, null, null,
                null);
    }

    private void appendHikariText(StringBuilder sb, DataSource ds) {
        Object poolName = invokeMethod(ds, "getPoolName");
        if (poolName != null) {
            sb.append(String.format("  Pool Name: %s%n", poolName));
        }
        Object maxPoolSize = invokeMethod(ds, "getMaximumPoolSize");
        if (maxPoolSize != null) {
            sb.append(String.format("  Max Pool Size: %s%n", maxPoolSize));
        }

        Object mxBean = invokeMethod(ds, "getHikariPoolMXBean");
        if (mxBean != null) {
            sb.append(String.format("  Active: %s%n", invokeMethod(mxBean, "getActiveConnections")));
            sb.append(String.format("  Idle: %s%n", invokeMethod(mxBean, "getIdleConnections")));
            sb.append(String.format("  Total: %s%n", invokeMethod(mxBean, "getTotalConnections")));
            sb.append(String.format("  Waiting: %s%n", invokeMethod(mxBean, "getThreadsAwaitingConnection")));
        } else {
            sb.append("  Pool not yet initialized\n");
        }
    }

    // ---- Agroal ----

    private Entry collectAgroalMetrics(String name, DataSource ds, String poolType) {
        Long active = null;
        Long idle = null;
        Long maxUsed = null;
        Long leakDetection = null;
        Long created = null;
        Object metrics = invokeMethod(ds, "getMetrics");
        if (metrics != null) {
            active = asLong(invokeMethod(metrics, "activeCount"));
            idle = asLong(invokeMethod(metrics, "availableCount"));
            maxUsed = asLong(invokeMethod(metrics, "maxUsedCount"));
            leakDetection = asLong(invokeMethod(metrics, "leakDetectionCount"));
            created = asLong(invokeMethod(metrics, "creationCount"));
        }

        // max pool size via configuration chain
        Long maxPoolSize = null;
        Object config = invokeMethod(ds, "getConfiguration");
        if (config != null) {
            Object poolConfig = invokeMethod(config, "connectionPoolConfiguration");
            if (poolConfig != null) {
                maxPoolSize = asLong(invokeMethod(poolConfig, "maxSize"));
            }
        }

        // compute total from active + idle
        Long total = active != null && idle != null ? active + idle : null;

        return new Entry(
                name, ds.getClass().getName(), poolType, null, maxPoolSize, active, idle, total, null, maxUsed,
                leakDetection, created);
    }

    private void appendAgroalText(StringBuilder sb, DataSource ds) {
        Object metrics = invokeMethod(ds, "getMetrics");
        if (metrics != null) {
            sb.append(String.format("  Active: %s%n", invokeMethod(metrics, "activeCount")));
            sb.append(String.format("  Available: %s%n", invokeMethod(metrics, "availableCount")));
            sb.append(String.format("  Max Used: %s%n", invokeMethod(metrics, "maxUsedCount")));
            sb.append(String.format("  Leak Detection: %s%n", invokeMethod(metrics, "leakDetectionCount")));
        }

        Object config = invokeMethod(ds, "getConfiguration");
        if (config != null) {
            Object poolConfig = invokeMethod(config, "connectionPoolConfiguration");
            if (poolConfig != null) {
                sb.append(String.format("  Max Pool Size: %s%n", invokeMethod(poolConfig, "maxSize")));
            }
        }
    }

    // ---- Reflection helpers ----

    private static Object invokeMethod(Object target, String methodName) {
        try {
            Method m = target.getClass().getMethod(methodName);
            return m.invoke(target);
        } catch (Exception e) {
            return null;
        }
    }

    private static Long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }
}
