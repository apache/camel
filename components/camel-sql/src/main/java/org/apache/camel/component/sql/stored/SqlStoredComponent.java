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

import javax.sql.DataSource;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.PropertiesHelper;
import org.springframework.jdbc.core.JdbcTemplate;

@Component("sql-stored")
public class SqlStoredComponent extends DefaultComponent {

    @Metadata(autowired = true)
    private DataSource dataSource;

    @Override
    protected Endpoint createEndpoint(String uri, String template, Map<String, Object> parameters) throws Exception {

        SqlStoredEndpoint endpoint = new SqlStoredEndpoint(uri, this);
        endpoint.setTemplate(template);
        setProperties(endpoint, parameters);

        // endpoint configured data source takes precedence
        DataSource ds = dataSource;
        if (endpoint.getDataSource() != null) {
            ds = endpoint.getDataSource();
        }
        if (ds == null) {
            throw new IllegalArgumentException("DataSource must be configured");
        }

        // create template
        JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);
        Map<String, Object> templateOptions = PropertiesHelper.extractProperties(parameters, "template.");
        PropertyBindingSupport.bindProperties(getCamelContext(), jdbcTemplate, templateOptions);

        // set template on endpoint
        endpoint.setJdbcTemplate(jdbcTemplate);
        endpoint.setDataSource(ds);
        endpoint.setTemplateOptions(templateOptions);

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
