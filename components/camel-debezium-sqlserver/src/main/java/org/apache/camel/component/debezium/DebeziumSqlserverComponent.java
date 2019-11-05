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

import org.apache.camel.CamelContext;
import org.apache.camel.component.debezium.configuration.SqlServerConnectorEmbeddedDebeziumConfiguration;
import org.apache.camel.spi.annotations.Component;

@Component("debezium-sqlserver")
public final class DebeziumSqlserverComponent extends DebeziumComponent<SqlServerConnectorEmbeddedDebeziumConfiguration> {

    private SqlServerConnectorEmbeddedDebeziumConfiguration configuration;

    public DebeziumSqlserverComponent() {
    }

    public DebeziumSqlserverComponent(final CamelContext context) {
        super(context);
    }

    /**
     * Allow pre-configured Configurations to be set.
     *
     * @return {@link SqlServerConnectorEmbeddedDebeziumConfiguration}
     */
    @Override
    public SqlServerConnectorEmbeddedDebeziumConfiguration getConfiguration() {
        if (configuration == null) {
            return new SqlServerConnectorEmbeddedDebeziumConfiguration();
        }
        return configuration;
    }

    @Override
    public void setConfiguration(SqlServerConnectorEmbeddedDebeziumConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected DebeziumEndpoint initializeDebeziumEndpoint(String uri, SqlServerConnectorEmbeddedDebeziumConfiguration configuration) {
        return new DebeziumSqlserverEndpoint(uri, this,  configuration);
    }
}
