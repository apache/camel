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
package org.apache.camel.component.sql;

import java.util.Map;

import javax.sql.DataSource;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.util.IntrospectionSupport;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * SQL Endpoint. Endpoint URI should contain valid SQL statement, but instead of
 * question marks (that are parameter placeholders), sharp signs should be used.
 * This is because in camel question mark has other meaning.
 *
 * @author romkal
 */
public class SqlEndpoint extends DefaultEndpoint {

    private JdbcTemplate jdbcTemplate;

    private String query;

    public SqlEndpoint(String uri, String query, Component component, DataSource dataSource, Map parameters) throws Exception {
        super(uri, component);
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        IntrospectionSupport.setProperties(jdbcTemplate, parameters, "template.");
        this.query = query;
    }

    public SqlEndpoint(String endpointUri, JdbcTemplate jdbcTemplate, String query) {
        super(endpointUri);
        this.jdbcTemplate = jdbcTemplate;
        this.query = query;
    }
    
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public Producer createProducer() throws Exception {
        return new SqlProducer(this, query, jdbcTemplate);
    }

    public boolean isSingleton() {
        return false;
    }

}
