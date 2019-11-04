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

import org.apache.camel.component.debezium.configuration.PostgresConnectorEmbeddedDebeziumConfiguration;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * Represents a Debezium PostgresSQL endpoint which is used to capture changes in PostgresSQL database so that that applications can see those changes and respond to them.
 */
@UriEndpoint(firstVersion = "3.0.0", scheme = "debezium-postgres", title = "Debezium PostgresSQL Connector", syntax = "debezium-postgres:name", label = "database,sql,postgres", consumerOnly = true)
public final class DebeziumPostgresEndpoint extends DebeziumEndpoint<PostgresConnectorEmbeddedDebeziumConfiguration> {

    @UriParam
    private PostgresConnectorEmbeddedDebeziumConfiguration configuration;

    public DebeziumPostgresEndpoint(final String uri, final DebeziumPostgresComponent component, final PostgresConnectorEmbeddedDebeziumConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    public DebeziumPostgresEndpoint() {
    }

    @Override
    public PostgresConnectorEmbeddedDebeziumConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public void setConfiguration(final PostgresConnectorEmbeddedDebeziumConfiguration configuration) {
        this.configuration = configuration;
    }
}
