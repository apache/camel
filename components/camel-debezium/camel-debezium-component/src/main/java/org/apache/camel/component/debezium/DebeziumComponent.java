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

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.component.debezium.configuration.ConfigurationValidation;
import org.apache.camel.component.debezium.configuration.EmbeddedDebeziumConfiguration;
import org.apache.camel.component.debezium.configuration.MySqlConnectorEmbeddedDebeziumConfiguration;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents the component that manages {@link DebeziumEndpoint}.
 */
@Component("debezium")
public class DebeziumComponent extends DefaultComponent {

    @UriParam
    private EmbeddedDebeziumConfiguration configuration;

    public DebeziumComponent() {
    }

    public DebeziumComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected DebeziumEndpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters)
        throws Exception {
        // check for type when configurations are not set explicitly
        if (ObjectHelper.isEmpty(remaining) && configuration == null) {
            throw new IllegalArgumentException("Connector type must be configured on endpoint using syntax debezium:type");
        }

        if (configuration == null) {
            // we will change to factory strategy in order to create the configurations once
            // we have more than one connector supported
            final DebeziumConnectorTypes connectorTypes = DebeziumConnectorTypes.fromString(remaining);
            if (connectorTypes == DebeziumConnectorTypes.MYSQL) {
                configuration = instantiate("org.apache.camel.component.debezium.configuration.MySqlConnectorEmbeddedDebeziumConfiguration", MySqlConnectorEmbeddedDebeziumConfiguration.class);
            } else {
                throw new IllegalArgumentException(String
                    .format("Connector of type '%s' is not supported yet.",
                            connectorTypes.getName().toLowerCase()));
            }
        }

        setProperties(configuration, parameters);

        // validate configurations
        final ConfigurationValidation configurationValidation = configuration.validateConfiguration();

        if (!configurationValidation.isValid()) {
            throw new IllegalArgumentException(configurationValidation.getReason());
        }

        return new DebeziumEndpoint(uri, this, configuration);
    }

    /**
     * Allow pre-configured Configurations to be set, you will need to extend
     * {@link MySqlConnectorEmbeddedDebeziumConfiguration} in order to create the configuration
     * for the component
     *
     * @return {@link MySqlConnectorEmbeddedDebeziumConfiguration}
     */
    public EmbeddedDebeziumConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(EmbeddedDebeziumConfiguration configuration) {
        if (this.configuration == null) {
            this.configuration = configuration;
        }
    }

    private <T> T instantiate(final String className, final Class<T> type){
        try{
            return type.cast(Class.forName(className).newInstance());
        } catch(InstantiationException
                | IllegalAccessException
                | ClassNotFoundException e){
            throw new IllegalStateException(e);
        }
    }
}
