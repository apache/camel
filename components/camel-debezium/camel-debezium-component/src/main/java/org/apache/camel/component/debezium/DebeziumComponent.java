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
import org.apache.camel.support.DefaultComponent;

/**
 * Base class for all debezium components
 */
public abstract class DebeziumComponent extends DefaultComponent {

    public DebeziumComponent() {
    }

    public DebeziumComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected DebeziumEndpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters)
            throws Exception {
        final EmbeddedDebeziumConfiguration configuration = getConfiguration();

        setProperties(configuration, parameters);

        // validate configurations
        final ConfigurationValidation configurationValidation = configuration.validateConfiguration();

        if (!configurationValidation.isValid()) {
            throw new IllegalArgumentException(configurationValidation.getReason());
        }

        return initializeDebeziumEndpoint(uri, configuration);
    }

    protected abstract DebeziumEndpoint initializeDebeziumEndpoint(final String uri, final EmbeddedDebeziumConfiguration configuration);

    public abstract EmbeddedDebeziumConfiguration getConfiguration();
}