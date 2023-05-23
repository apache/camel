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
package org.apache.camel.component.debezium;

import org.apache.camel.Category;
import org.apache.camel.component.debezium.configuration.SqlServerConnectorEmbeddedDebeziumConfiguration;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * Capture changes from an SQL Server database.
 */
@UriEndpoint(firstVersion = "3.0.0", scheme = "debezium-sqlserver", title = "Debezium SQL Server Connector",
             syntax = "debezium-sqlserver:name", category = { Category.DATABASE }, consumerOnly = true,
             headersClass = DebeziumConstants.class)
public final class DebeziumSqlserverEndpoint extends DebeziumEndpoint<SqlServerConnectorEmbeddedDebeziumConfiguration> {

    @UriParam
    private SqlServerConnectorEmbeddedDebeziumConfiguration configuration;

    public DebeziumSqlserverEndpoint(final String uri, final DebeziumSqlserverComponent component,
                                     final SqlServerConnectorEmbeddedDebeziumConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    public DebeziumSqlserverEndpoint() {
    }

    @Override
    public SqlServerConnectorEmbeddedDebeziumConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public void setConfiguration(final SqlServerConnectorEmbeddedDebeziumConfiguration configuration) {
        this.configuration = configuration;
    }
}
