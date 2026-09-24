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
package org.apache.camel.component.jdbc;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.apache.camel.Endpoint;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.SecretRotationAware;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.PropertiesHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component("jdbc")
public class JdbcComponent extends DefaultComponent implements SecretRotationAware {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcComponent.class);

    @Metadata
    private DataSource dataSource;
    @Metadata(label = "advanced")
    private ConnectionStrategy connectionStrategy;

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        DataSource dataSource;
        String dataSourceRef;

        if (this.dataSource != null) {
            // prefer to use datasource set by setter
            dataSource = this.dataSource;
            dataSourceRef = "component";
        } else {
            DataSource target = CamelContextHelper.lookup(getCamelContext(), remaining, DataSource.class);
            if (target == null && !isDefaultDataSourceName(remaining)) {
                throw new NoSuchBeanException(remaining, DataSource.class.getName());
            } else if (target == null) {
                // check if the registry contains a single instance of DataSource
                Set<DataSource> dataSources = getCamelContext().getRegistry().findByType(DataSource.class);
                if (dataSources.size() > 1) {
                    throw new IllegalArgumentException(
                            "Multiple DataSources found in the registry and no explicit configuration provided");
                } else if (dataSources.size() == 1) {
                    target = dataSources.iterator().next();
                }
                if (target == null) {
                    throw new IllegalArgumentException("No default DataSource found in the registry");
                }
                LOG.debug("Using default DataSource discovered from registry: {}", target);
            }
            dataSource = target;
            dataSourceRef = remaining;
        }

        Map<String, Object> params = PropertiesHelper.extractProperties(parameters, "statement.");

        JdbcEndpoint jdbc = createEndpoint(uri, this, dataSource);
        if (connectionStrategy != null) {
            jdbc.setConnectionStrategy(connectionStrategy);
        }
        jdbc.setDataSourceName(dataSourceRef);
        jdbc.setParameters(params);
        setProperties(jdbc, parameters);

        return jdbc;
    }

    protected JdbcEndpoint createEndpoint(String uri, JdbcComponent component, DataSource dataSource) {
        return new JdbcEndpoint(uri, component, dataSource);
    }

    /**
     * To use the {@link DataSource} instance instead of looking up the data source by name from the registry.
     */
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public ConnectionStrategy getConnectionStrategy() {
        return connectionStrategy;
    }

    /**
     * To use a custom strategy for working with connections.
     *
     * Do not use a custom strategy when using the spring-jdbc component because a special Spring ConnectionStrategy is
     * used by default to support Spring Transactions.
     */
    public void setConnectionStrategy(ConnectionStrategy connectionStrategy) {
        this.connectionStrategy = connectionStrategy;
    }

    @Override
    public void onSecretRotation(Object source) throws Exception {
        // Collect all DataSources this component can reach: the one injected directly (if any)
        // plus all DataSource beans registered in the registry. The registry beans are typically
        // not recreated during a route reload, so their connection pools still hold connections
        // that were authenticated with the old credentials.
        Set<DataSource> dataSources = getCamelContext().getRegistry().findByType(DataSource.class);
        if (this.dataSource != null) {
            dataSources.add(this.dataSource);
        }

        for (DataSource ds : dataSources) {
            evictDataSourceConnections(ds, source);
        }
    }

    /**
     * Evicts stale connections from the given DataSource so that the pool rebuilds them with the rotated credentials.
     * <p/>
     * HikariCP is tried first via reflection (so camel-jdbc does not need a compile-time dependency on it). Any
     * DataSource that does not expose a {@code softEvictConnections()} method is left untouched — the pool will pick up
     * the new credentials on its own reconnect cycle when existing connections expire.
     */
    static void evictDataSourceConnections(DataSource ds, Object source) {
        // HikariCP: softEvictConnections() marks all current connections for eviction while
        // allowing in-flight queries to complete; the pool then recreates them with the new credentials.
        try {
            Method softEvict = ds.getClass().getMethod("softEvictConnections");
            softEvict.invoke(ds);
            LOG.info("Secret rotation (source={}): HikariCP softEvictConnections() called on {}", source, ds);
            return;
        } catch (NoSuchMethodException e) {
            // Not a HikariCP DataSource — fall through to generic handling
        } catch (Exception e) {
            LOG.warn("Secret rotation (source={}): softEvictConnections() failed on {}: {}", source, ds, e.getMessage());
        }

        // Generic fallback: log that the pool was not explicitly evicted.
        // The pool will pick up the new credentials when existing connections expire naturally.
        LOG.info(
                "Secret rotation (source={}): DataSource {} does not expose softEvictConnections(); "
                 + "existing connections will be replaced as they expire or are validated",
                source, ds.getClass().getName());
    }

    private static boolean isDefaultDataSourceName(String remaining) {
        return "dataSource".equalsIgnoreCase(remaining) || "default".equalsIgnoreCase(remaining);
    }
}
