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
package org.apache.camel.component.duckdb;

import java.util.Map;

import javax.sql.DataSource;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;

@Component("duckdb")
public class DuckDbComponent extends DefaultComponent {

    @Metadata(autowired = true, description = "Shared JDBC DataSource for all endpoints.")
    private DataSource dataSource;
    @Metadata(description = "Full JDBC URL override (for example jdbc:duckdb:/path/to/db). When set, databasePath is ignored.")
    private String jdbcUrl;
    @Metadata(defaultValue = ":memory:",
              description = "Embedded database path (:memory: or a file path). Used when jdbcUrl is not set.")
    private String databasePath = ":memory:";

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        DuckDbEndpoint endpoint = new DuckDbEndpoint(uri, this);
        endpoint.setDataSource(dataSource);
        if (ObjectHelper.isNotEmpty(jdbcUrl)) {
            endpoint.setJdbcUrl(jdbcUrl);
        }

        String databasePathValue = ObjectHelper.isEmpty(remaining) ? databasePath : remaining;
        if (ObjectHelper.isEmpty(databasePathValue)) {
            databasePathValue = ":memory:";
        }
        endpoint.setDatabasePath(databasePathValue);

        setProperties(endpoint, parameters);
        return endpoint;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public String getDatabasePath() {
        return databasePath;
    }

    public void setDatabasePath(String databasePath) {
        this.databasePath = databasePath;
    }
}
