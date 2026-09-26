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
package org.apache.camel.support;

import java.lang.reflect.Method;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods for working with JDBC {@link DataSource} instances.
 */
public final class DataSourceHelper {

    private static final Logger LOG = LoggerFactory.getLogger(DataSourceHelper.class);

    private DataSourceHelper() {
    }

    /**
     * Evicts stale connections from the given DataSource so that the pool rebuilds them with the rotated credentials.
     * <p/>
     * HikariCP is tried first via reflection (so the caller does not need a compile-time dependency on it). Any
     * DataSource that does not expose {@code getHikariPoolMXBean()} is left untouched — the pool will pick up the new
     * credentials on its own reconnect cycle when existing connections expire.
     * <p/>
     * <b>Important:</b> this method only evicts existing connections from the pool. It does <em>not</em> update the
     * pool's credentials. For pools configured with a static password (e.g. Spring Boot
     * {@code spring.datasource.password}, Quarkus {@code quarkus.datasource.jdbc.url}), the pool will re-open
     * connections using the <em>old</em> credentials unless the credentials provider resolves them dynamically (e.g.
     * {@code HikariCredentialsProvider}, the AWS JDBC wrapper secrets plugin, or a custom {@code DataSource} that
     * fetches credentials from a vault at connect time).
     *
     * @param ds     the DataSource whose connections should be evicted
     * @param source an opaque label for the rotation event (used in log messages only)
     */
    public static void evictDataSourceConnections(DataSource ds, Object source) {
        // HikariCP: softEvictConnections() is defined on HikariPoolMXBean, not on HikariDataSource directly.
        // We retrieve the MXBean via getHikariPoolMXBean() (a public method on HikariDataSource) using reflection
        // so that the caller does not need a compile-time dependency on HikariCP.
        try {
            Method getPoolMXBean = ds.getClass().getMethod("getHikariPoolMXBean");
            Object poolMXBean = getPoolMXBean.invoke(ds);
            if (poolMXBean != null) {
                Method softEvict = poolMXBean.getClass().getMethod("softEvictConnections");
                softEvict.invoke(poolMXBean);
                LOG.info("Secret rotation (source={}): HikariCP softEvictConnections() called on {}", source, ds);
            } else {
                LOG.debug("Secret rotation (source={}): HikariCP pool on {} is not started yet; nothing to evict",
                        source, ds);
            }
            return;
        } catch (NoSuchMethodException e) {
            // Not a HikariCP DataSource — fall through to generic handling
        } catch (Exception e) {
            LOG.warn("Secret rotation (source={}): softEvictConnections() failed on {}: {}", source, ds, e.getMessage());
        }

        // Generic fallback: log that the pool was not explicitly evicted.
        // The pool will pick up the new credentials when existing connections expire naturally.
        LOG.info(
                "Secret rotation (source={}): DataSource {} does not support HikariCP pool eviction; "
                 + "existing connections will be replaced as they expire or are validated",
                source, ds.getClass().getName());
    }
}
