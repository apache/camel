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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Function;

import javax.sql.DataSource;

import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.InterceptSendToEndpoint;
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
     * Collects and evicts stale connections from all DataSources used by a component: the component's own
     * {@code dataSource} field plus any DataSources held by the component's active endpoints.
     * <p/>
     * Identity-based deduplication ensures each DataSource is evicted at most once, even if the same instance is shared
     * between the component and one or more endpoints.
     * <p/>
     * {@link InterceptSendToEndpoint} wrappers are unwrapped to the original endpoint before the component check and
     * DataSource extraction. This ensures that intercepted endpoints (created by {@code interceptSendToEndpoint()}) are
     * not silently skipped.
     *
     * @param componentDataSource the component-level DataSource (may be null)
     * @param endpoints           the component's active endpoints (from {@code getCamelContext().getEndpoints()})
     * @param componentInstance   the component instance, used to filter endpoints
     *                            ({@code endpoint.getComponent() == this})
     * @param endpointDsExtractor a function to extract the DataSource from an endpoint (may return null)
     * @param source              an opaque label for the rotation event (used in log messages only)
     */
    public static void evictComponentDataSources(
            DataSource componentDataSource,
            Iterable<Endpoint> endpoints,
            Component componentInstance,
            Function<Endpoint, DataSource> endpointDsExtractor,
            Object source) {

        Set<DataSource> dataSources = Collections.newSetFromMap(new IdentityHashMap<>());
        if (componentDataSource != null) {
            dataSources.add(componentDataSource);
        }
        if (endpoints != null) {
            for (Endpoint ep : endpoints) {
                // unwrap InterceptSendToEndpoint proxies so that the component check and extractor
                // see the real endpoint type (e.g. JdbcEndpoint, DefaultSqlEndpoint)
                Endpoint target = ep instanceof InterceptSendToEndpoint ise ? ise.getOriginalEndpoint() : ep;
                if (target.getComponent() == componentInstance) {
                    DataSource ds = endpointDsExtractor.apply(target);
                    if (ds != null) {
                        dataSources.add(ds);
                    }
                }
            }
        }
        for (DataSource ds : dataSources) {
            evictDataSourceConnections(ds, source);
        }
    }

    /**
     * Evicts stale connections from the given DataSource's connection pool.
     * <p/>
     * The following pool implementations are supported (detected via reflection — no compile-time dependency required):
     * <ul>
     * <li><b>HikariCP</b>: {@code softEvictConnections()} is called via {@code HikariPoolMXBean}. Idle connections are
     * evicted immediately; borrowed connections are evicted when returned.</li>
     * <li><b>Agroal</b> (Quarkus default): {@code flush(GRACEFUL)} is called on {@code AgroalDataSource}. Idle
     * connections are closed immediately; active connections are closed when returned to the pool.</li>
     * </ul>
     * Delegating wrappers (Spring's {@code TransactionAwareDataSourceProxy}, {@code LazyConnectionDataSourceProxy},
     * P6Spy, etc.) are supported via pool-type-specific {@code isWrapperFor(poolType)} / {@code unwrap(poolType)} on
     * {@link java.sql.Wrapper}: the wrapper's {@code unwrap(HikariDataSource.class)} delegates to its target's
     * {@code unwrap}, so the actual pool is reached regardless of how many layers sit on top.
     * <p/>
     * Any DataSource not recognised as one of the above is left untouched — a log message is emitted and the pool will
     * naturally replace connections as they expire or are validated.
     * <p/>
     * <b>Important:</b> this method only evicts existing connections from the pool. It does <em>not</em> update the
     * pool's credentials. For pools configured with a static password (e.g. Spring Boot
     * {@code spring.datasource.password}), the pool will re-open connections using the <em>old</em> credentials unless
     * the credentials are resolved dynamically (e.g. {@code HikariCredentialsProvider} (HikariCP 7.0 or later), the AWS
     * JDBC wrapper secrets plugin, or a custom {@code DataSource} that fetches credentials from a vault at connect
     * time).
     *
     * @param ds     the DataSource whose connections should be evicted
     * @param source an opaque label for the rotation event (used in log messages only)
     */
    public static void evictDataSourceConnections(DataSource ds, Object source) {
        // HikariCP: softEvictConnections() is defined on HikariPoolMXBean, not on HikariDataSource directly.
        // Detection strategy (in order):
        // 1. Class-based: use isWrapperFor/unwrap(HikariDataSource.class) to handle delegating wrappers
        //    (Spring's TransactionAwareDataSourceProxy, LazyConnectionDataSourceProxy, P6Spy, etc.).
        // 2. Duck-typing fallback: call getHikariPoolMXBean() directly on the DataSource if the method
        //    exists, even when HikariCP is not on the classpath or the DataSource doesn't extend
        //    HikariDataSource (covers custom wrappers and test doubles that expose the HikariCP API).
        try {
            DataSource hikariDs = null;
            try {
                Class<?> hikariDsClass
                        = Class.forName("com.zaxxer.hikari.HikariDataSource", false, ds.getClass().getClassLoader());
                if (hikariDsClass.isInstance(ds)) {
                    hikariDs = ds;
                } else {
                    try {
                        if (ds.isWrapperFor(hikariDsClass)) {
                            hikariDs = (DataSource) ds.unwrap(hikariDsClass);
                        }
                    } catch (Exception e) {
                        LOG.trace(
                                "Secret rotation (source={}): isWrapperFor/unwrap(HikariDataSource) failed on {}",
                                source, ds.getClass().getName(), e);
                    }
                }
            } catch (ClassNotFoundException e) {
                // HikariCP not on classpath — duck-typing fallback below will still handle wrappers that
                // expose getHikariPoolMXBean()
            }
            // Duck-typing fallback: if class-based detection didn't find a HikariDataSource, try calling
            // getHikariPoolMXBean() directly on the DataSource via reflection. This handles:
            //   - Wrappers that expose the HikariCP API without extending HikariDataSource
            //   - DataSources used in tests that simulate the HikariCP API
            //   - Cases where HikariCP is not on the classpath but a compatible API is present
            if (hikariDs == null) {
                try {
                    ds.getClass().getMethod("getHikariPoolMXBean");
                    // The method exists on this DataSource — treat it as a HikariCP-compatible pool.
                    hikariDs = ds;
                } catch (NoSuchMethodException e) {
                    // No getHikariPoolMXBean() on this DataSource — not a HikariCP-compatible pool
                }
            }
            if (hikariDs != null) {
                Method getPoolMXBean = hikariDs.getClass().getMethod("getHikariPoolMXBean");
                Object poolMXBean = getPoolMXBean.invoke(hikariDs);
                if (poolMXBean != null) {
                    Method softEvict = poolMXBean.getClass().getMethod("softEvictConnections");
                    softEvict.invoke(poolMXBean);
                    LOG.info("Secret rotation (source={}): HikariCP softEvictConnections() called on {}", source, ds);
                } else {
                    LOG.debug("Secret rotation (source={}): HikariCP pool on {} is not started yet; nothing to evict",
                            source, ds);
                }
                return;
            }
        } catch (NoSuchMethodException e) {
            // getHikariPoolMXBean or softEvictConnections not found — not a supported HikariCP version?
            // fall through
        } catch (InvocationTargetException e) {
            LOG.warn("Secret rotation (source={}): HikariCP softEvictConnections() failed on {}", source, ds,
                    e.getCause() != null ? e.getCause() : e);
            return;
        } catch (Exception e) {
            LOG.warn("Secret rotation (source={}): HikariCP softEvictConnections() failed on {}", source, ds, e);
            return;
        }

        // Agroal (Quarkus default pool): AgroalDataSource.flush(FlushMode.GRACEFUL).
        // GRACEFUL closes idle connections immediately and active connections when returned to the pool.
        // Use isWrapperFor/unwrap(AgroalDataSource.class) to handle delegating wrappers the same way
        // as for HikariCP above.  No compile-time dependency on agroal-api.
        try {
            Class<?> agroalDsClass
                    = Class.forName("io.agroal.api.AgroalDataSource", false, ds.getClass().getClassLoader());
            Object agroalDs = null;
            if (agroalDsClass.isInstance(ds)) {
                agroalDs = ds;
            } else {
                try {
                    if (ds.isWrapperFor(agroalDsClass)) {
                        agroalDs = ds.unwrap(agroalDsClass);
                    }
                } catch (Exception e) {
                    LOG.trace(
                            "Secret rotation (source={}): isWrapperFor/unwrap(AgroalDataSource) failed on {}",
                            source, ds.getClass().getName(), e);
                }
            }
            if (agroalDs != null) {
                // resolve flush(FlushMode) from the public interface
                for (Method m : agroalDsClass.getMethods()) {
                    if ("flush".equals(m.getName()) && m.getParameterCount() == 1) {
                        Class<?> flushModeClass = m.getParameterTypes()[0];
                        if (flushModeClass.isEnum() && flushModeClass.getSimpleName().equals("FlushMode")) {
                            @SuppressWarnings("unchecked")
                            Enum<?> graceful = Enum.valueOf((Class<Enum>) flushModeClass, "GRACEFUL");
                            m.invoke(agroalDs, graceful);
                            LOG.info("Secret rotation (source={}): Agroal flush(GRACEFUL) called on {}", source, ds);
                            return;
                        }
                    }
                }
                // Agroal was recognised but flush(FlushMode) was not found — API mismatch
                LOG.warn("Secret rotation (source={}): Agroal DataSource {} was recognised but "
                         + "flush(FlushMode) method was not found — pool connections will not be evicted",
                        source, ds.getClass().getName());
                return;
            }
        } catch (ClassNotFoundException e) {
            // Agroal not on classpath — fall through to generic fallback
        } catch (InvocationTargetException e) {
            LOG.warn("Secret rotation (source={}): Agroal flush(GRACEFUL) failed on {}", source, ds,
                    e.getCause() != null ? e.getCause() : e);
            return;
        } catch (Exception e) {
            LOG.warn("Secret rotation (source={}): Agroal flush(GRACEFUL) failed on {}", source, ds, e);
            return;
        }

        // Generic fallback: log that the pool was not explicitly evicted.
        LOG.info(
                "Secret rotation (source={}): DataSource {} is not a recognised connection pool (HikariCP, Agroal); "
                 + "existing connections will be replaced as they expire or are validated",
                source, ds.getClass().getName());
    }
}
