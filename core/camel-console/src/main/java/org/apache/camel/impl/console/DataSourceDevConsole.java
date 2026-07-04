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
import java.util.Map;

import javax.sql.DataSource;

import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "datasource", displayName = "DataSource", description = "Displays DataSource connection pool metrics")
@Configurer(extended = true)
public class DataSourceDevConsole extends AbstractDevConsole {

    private static final String HIKARI_CLASS = "com.zaxxer.hikari.HikariDataSource";
    private static final String AGROAL_CLASS = "io.agroal.api.AgroalDataSource";

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
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();

        Map<String, DataSource> dataSources
                = getCamelContext().getRegistry().findByTypeWithName(DataSource.class);

        for (Map.Entry<String, DataSource> entry : dataSources.entrySet()) {
            DataSource ds = entry.getValue();
            String poolType = detectPoolType(ds);

            JsonObject jo = new JsonObject();
            jo.put("name", entry.getKey());
            jo.put("type", ds.getClass().getName());
            jo.put("poolType", poolType);

            if ("HikariCP".equals(poolType)) {
                collectHikariMetrics(jo, ds);
            } else if ("Agroal".equals(poolType)) {
                collectAgroalMetrics(jo, ds);
            }

            arr.add(jo);
        }

        root.put("dataSources", arr);
        return root;
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

    private void collectHikariMetrics(JsonObject jo, DataSource ds) {
        Object poolName = invokeMethod(ds, "getPoolName");
        if (poolName != null) {
            jo.put("poolName", String.valueOf(poolName));
        }
        Object maxPoolSize = invokeMethod(ds, "getMaximumPoolSize");
        if (maxPoolSize instanceof Number n) {
            jo.put("maxPoolSize", n.intValue());
        }

        Object mxBean = invokeMethod(ds, "getHikariPoolMXBean");
        if (mxBean != null) {
            putInt(jo, "active", invokeMethod(mxBean, "getActiveConnections"));
            putInt(jo, "idle", invokeMethod(mxBean, "getIdleConnections"));
            putInt(jo, "total", invokeMethod(mxBean, "getTotalConnections"));
            putInt(jo, "waiting", invokeMethod(mxBean, "getThreadsAwaitingConnection"));
        }
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

    private void collectAgroalMetrics(JsonObject jo, DataSource ds) {
        Object metrics = invokeMethod(ds, "getMetrics");
        if (metrics != null) {
            putLong(jo, "active", invokeMethod(metrics, "activeCount"));
            putLong(jo, "idle", invokeMethod(metrics, "availableCount"));
            putLong(jo, "maxUsed", invokeMethod(metrics, "maxUsedCount"));
            putLong(jo, "leakDetection", invokeMethod(metrics, "leakDetectionCount"));
            putLong(jo, "created", invokeMethod(metrics, "creationCount"));
        }

        // max pool size via configuration chain
        Object config = invokeMethod(ds, "getConfiguration");
        if (config != null) {
            Object poolConfig = invokeMethod(config, "connectionPoolConfiguration");
            if (poolConfig != null) {
                Object maxSize = invokeMethod(poolConfig, "maxSize");
                if (maxSize instanceof Number n) {
                    jo.put("maxPoolSize", n.intValue());
                }
            }
        }

        // compute total from active + idle
        Object active = jo.get("active");
        Object idle = jo.get("idle");
        if (active instanceof Number a && idle instanceof Number i) {
            jo.put("total", a.longValue() + i.longValue());
        }
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

    private static void putInt(JsonObject jo, String key, Object value) {
        if (value instanceof Number n) {
            jo.put(key, n.intValue());
        }
    }

    private static void putLong(JsonObject jo, String key, Object value) {
        if (value instanceof Number n) {
            jo.put(key, n.longValue());
        }
    }
}
