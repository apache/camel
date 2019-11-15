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
package org.apache.camel.component.sql.stored;

import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.springframework.jdbc.core.JdbcTemplate;

@Component("sql-stored")
public class SqlStoredComponent extends DefaultComponent {

    private DataSource dataSource;

    @Override
    protected Endpoint createEndpoint(String uri, String template, Map<String, Object> parameters) throws Exception {
        DataSource target = null;

        // endpoint options overrule component configured datasource
        DataSource ds = resolveAndRemoveReferenceParameter(parameters, "dataSource", DataSource.class);
        if (ds != null) {
            target = ds;
        }
        if (target == null) {
            // fallback and use component
            target = dataSource;
        }
        if (target == null) {
            // check if the registry contains a single instance of DataSource
            Set<DataSource> dataSources = getCamelContext().getRegistry().findByType(DataSource.class);
            if (dataSources.size() > 1) {
                throw new IllegalArgumentException("Multiple DataSources found in the registry and no explicit configuration provided");
            } else if (dataSources.size() == 1) {
                target = dataSources.stream().findFirst().orElse(null);
            }
        }
        if (target == null) {
            throw new IllegalArgumentException("DataSource must be configured");
        }

        JdbcTemplate jdbcTemplate = new JdbcTemplate(target);

        SqlStoredEndpoint endpoint = new SqlStoredEndpoint(uri, this, jdbcTemplate);
        endpoint.setTemplate(template);
        return endpoint;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Sets the DataSource to use to communicate with the database.
     */
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }
}
