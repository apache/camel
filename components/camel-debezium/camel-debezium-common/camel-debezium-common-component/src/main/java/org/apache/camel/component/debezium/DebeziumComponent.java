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

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.component.debezium.configuration.ConfigurationValidation;
import org.apache.camel.component.debezium.configuration.EmbeddedDebeziumConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.PropertiesHelper;

/**
 * Base class for all debezium components
 */
public abstract class DebeziumComponent<C extends EmbeddedDebeziumConfiguration> extends DefaultComponent {

    private static final String PREFIX_CAMEL_KAMELET = "camel.kamelet.";
    private static final Pattern KAMELET_PATTERN = Pattern.compile(PREFIX_CAMEL_KAMELET + "(.*?)\\.source\\.(.*?)");

    protected DebeziumComponent() {
    }

    protected DebeziumComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected DebeziumEndpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters)
            throws Exception {
        final C configuration = getConfiguration();

        if (ObjectHelper.isEmpty(remaining) && ObjectHelper.isEmpty(configuration.getName())) {
            throw new IllegalArgumentException(
                    String.format("Connector name must be configured on endpoint using syntax debezium-%s:name",
                            configuration.getConnectorDatabaseType()));
        }

        // if we have name in path, we override the name in the configuration
        if (!ObjectHelper.isEmpty(remaining)) {
            configuration.setName(remaining);
        }

        if (parameters == null) {
            parameters = new HashMap<>();
        }
        // read kamelets propertiesComponentDslMojo
        extractKameletProperties(getCamelContext(), parameters);

        DebeziumEndpoint endpoint = initializeDebeziumEndpoint(uri, configuration);
        setProperties(endpoint, parameters);

        // extract the additional properties map
        if (PropertiesHelper.hasProperties(parameters, "additionalProperties.")) {
            final Map<String, Object> additionalProperties = endpoint.getConfiguration().getAdditionalProperties();

            // add and overwrite additional properties from endpoint to pre-configured properties
            additionalProperties.putAll(PropertiesHelper.extractProperties(parameters, "additionalProperties."));
        }

        // validate configurations
        final ConfigurationValidation configurationValidation = configuration.validateConfiguration();

        if (!configurationValidation.isValid()) {
            throw new IllegalArgumentException(configurationValidation.getReason());
        }

        return endpoint;
    }

    protected abstract DebeziumEndpoint initializeDebeziumEndpoint(String uri, C configuration);

    public abstract C getConfiguration();

    @Metadata(description = "Component configuration")
    public abstract void setConfiguration(C configuration);

    private void extractKameletProperties(CamelContext context, Map<String, Object> parameters) {
        // How to get the kamelet name by defined ?
        // camel.kamelet.[kameletName].source.key = val
        PropertiesComponent pc = context.getPropertiesComponent();
        Properties prefixed = pc.loadProperties(name -> name.startsWith(PREFIX_CAMEL_KAMELET));
        for (String name : prefixed.stringPropertyNames()) {
            Matcher matcher = KAMELET_PATTERN.matcher(name);
            if (matcher.find()) {
                String kameletKey = name.substring(matcher.group().length());
                parameters.putIfAbsent(kameletKey, prefixed.getProperty(name));
            }
        }
    }
}
