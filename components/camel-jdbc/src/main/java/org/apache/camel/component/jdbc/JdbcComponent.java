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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.apache.camel.Endpoint;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.PropertiesHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component("jdbc")
public class JdbcComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcComponent.class);

    @Metadata
    private DataSource dataSource;
    @Metadata(label = "advanced")
    private ConnectionStrategy connectionStrategy;

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        DataSource dataSource;
        String dataSourceRef;
        JdbcEndpoint endpoint = createEndpoint(uri, this, null);

        if (this.dataSource != null) {
            // prefer to use datasource set by setter
            dataSource = this.dataSource;
            dataSourceRef = "component";
        } else {
            // Initialize only dataSourceFactory, since it is used to create beans of type DataSource
            if (parameters.containsKey("dataSourceFactory")) {
                Map<String, Object> m = new HashMap<>(1);
                m.put("dataSourceFactory", parameters.get("dataSourceFactory"));
                setProperties(endpoint, m);
                if (endpoint.getDataSourceFactory() != null) {
                    endpoint.getDataSourceFactory().setCamelContext(getCamelContext());
                    endpoint.getDataSourceFactory().createDataSource(endpoint);

                    parameters.remove("dataSourceFactory");
                }
            }

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
                LOG.debug("Using default DataSource discovered from registry: {}", target);
            }
            dataSource = target;
            dataSourceRef = remaining;
        }

        Map<String, Object> params = PropertiesHelper.extractProperties(parameters, "statement.");

        endpoint.setDataSource(dataSource);
        if (connectionStrategy != null) {
            endpoint.setConnectionStrategy(connectionStrategy);
        }
        endpoint.setDataSourceName(dataSourceRef);
        endpoint.setParameters(params);
        setProperties(endpoint, parameters);

        return endpoint;
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

    private static boolean isDefaultDataSourceName(String remaining) {
        return "dataSource".equalsIgnoreCase(remaining) || "default".equalsIgnoreCase(remaining);
    }
}
