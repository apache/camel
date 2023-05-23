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
import org.apache.camel.component.debezium.configuration.OracleConnectorEmbeddedDebeziumConfiguration;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * Capture changes from a Oracle database.
 */
@UriEndpoint(firstVersion = "3.17.0", scheme = "debezium-oracle", title = "Debezium Oracle Connector",
             syntax = "debezium-oracle:name", category = { Category.DATABASE }, consumerOnly = true,
             headersClass = DebeziumConstants.class)
public final class DebeziumOracleEndpoint extends DebeziumEndpoint<OracleConnectorEmbeddedDebeziumConfiguration> {

    @UriParam
    private OracleConnectorEmbeddedDebeziumConfiguration configuration;

    public DebeziumOracleEndpoint(final String uri, final DebeziumOracleComponent component,
                                  final OracleConnectorEmbeddedDebeziumConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    public DebeziumOracleEndpoint() {
    }

    @Override
    public OracleConnectorEmbeddedDebeziumConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public void setConfiguration(final OracleConnectorEmbeddedDebeziumConfiguration configuration) {
        this.configuration = configuration;
    }
}
