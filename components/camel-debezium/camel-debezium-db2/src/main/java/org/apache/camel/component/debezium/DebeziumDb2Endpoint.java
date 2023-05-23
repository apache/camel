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
import org.apache.camel.component.debezium.configuration.Db2ConnectorEmbeddedDebeziumConfiguration;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * Capture changes from a DB2 database.
 */
@UriEndpoint(firstVersion = "3.17.0", scheme = "debezium-db2", title = "Debezium DB2 Connector",
             syntax = "debezium-db2:name", category = { Category.DATABASE }, consumerOnly = true,
             headersClass = DebeziumConstants.class)
public final class DebeziumDb2Endpoint extends DebeziumEndpoint<Db2ConnectorEmbeddedDebeziumConfiguration> {

    @UriParam
    private Db2ConnectorEmbeddedDebeziumConfiguration configuration;

    public DebeziumDb2Endpoint(final String uri, final DebeziumDb2Component component,
                               final Db2ConnectorEmbeddedDebeziumConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    public DebeziumDb2Endpoint() {
    }

    @Override
    public Db2ConnectorEmbeddedDebeziumConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public void setConfiguration(final Db2ConnectorEmbeddedDebeziumConfiguration configuration) {
        this.configuration = configuration;
    }
}
