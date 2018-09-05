/**
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

import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;

import org.apache.camel.Endpoint;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.IntrospectionSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version
 */
public class JdbcComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcComponent.class);
    private DataSource dataSource;

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
                    throw new IllegalArgumentException("Multiple DataSources found in the registry and no explicit configuration provided");
                } else if (dataSources.size() == 1) {
                    target = dataSources.stream().findFirst().orElse(null);
                }
                if (target == null) {
                    throw new IllegalArgumentException("No default DataSource found in the registry");
                }
                LOG.debug("Using default DataSource discovered from registry: {}", target);
            }
            dataSource = target;
            dataSourceRef = remaining;
        }

        Map<String, Object> params = IntrospectionSupport.extractProperties(parameters, "statement.");

        JdbcEndpoint jdbc = new JdbcEndpoint(uri, this, dataSource);
        jdbc.setDataSourceName(dataSourceRef);
        jdbc.setParameters(params);
        setProperties(jdbc, parameters);

        return jdbc;
    }

    /**
     * To use the {@link DataSource} instance instead of looking up the data source by name from the registry.
     */
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private static boolean isDefaultDataSourceName(String remaining) {
        return "dataSource".equalsIgnoreCase(remaining) || "default".equalsIgnoreCase(remaining);
    }
}
