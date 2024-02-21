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
import org.apache.camel.component.debezium.configuration.Db2ConnectorEmbeddedDebeziumConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;

@Component("debezium-db2")
public final class DebeziumDb2Component extends DebeziumComponent<Db2ConnectorEmbeddedDebeziumConfiguration> {

    @Metadata
    private Db2ConnectorEmbeddedDebeziumConfiguration configuration = new Db2ConnectorEmbeddedDebeziumConfiguration();

    public DebeziumDb2Component() {
    }

    public DebeziumDb2Component(final CamelContext context) {
        super(context);
    }

    /**
     * Allow pre-configured Configurations to be set.
     */
    @Override
    public Db2ConnectorEmbeddedDebeziumConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public void setConfiguration(Db2ConnectorEmbeddedDebeziumConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected DebeziumEndpoint<Db2ConnectorEmbeddedDebeziumConfiguration> initializeDebeziumEndpoint(
            String uri, Db2ConnectorEmbeddedDebeziumConfiguration configuration) {
        return new DebeziumDb2Endpoint(uri, this, configuration);
    }
}
