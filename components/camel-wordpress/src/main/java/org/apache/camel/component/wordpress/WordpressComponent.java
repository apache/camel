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
package org.apache.camel.component.wordpress;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

/**
 * Represents the component that manages {@link WordpressEndpoint}.
 */
@Component("wordpress")
public class WordpressComponent extends DefaultComponent {

    private static final String OP_SEPARATOR = ":";

    @Metadata(label = "advanced", description = "Wordpress component configuration")
    private WordpressComponentConfiguration configuration;

    public WordpressComponent() {
        this(new WordpressComponentConfiguration());
    }

    public WordpressComponent(WordpressComponentConfiguration configuration) {
        this.configuration = configuration;
    }

    public WordpressComponent(CamelContext camelContext) {
        super(camelContext);
        this.configuration = new WordpressComponentConfiguration();
    }

    public WordpressComponentConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(WordpressComponentConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (configuration != null) {
            Map<String, Object> properties = new HashMap<>();
            BeanIntrospection beanIntrospection = getCamelContext().adapt(ExtendedCamelContext.class).getBeanIntrospection();
            beanIntrospection.getProperties(configuration, properties, null, false);
            properties.forEach(parameters::putIfAbsent);
        }
        WordpressComponentConfiguration config = new WordpressComponentConfiguration();
        WordpressEndpoint endpoint = new WordpressEndpoint(uri, this, config);
        discoverOperations(endpoint, remaining);
        setProperties(endpoint, parameters);
        setProperties(config, parameters);
        return endpoint;
    }

    private void discoverOperations(WordpressEndpoint endpoint, String remaining) {
        final String[] operations = remaining.split(OP_SEPARATOR);
        endpoint.setOperation(operations[0]);
        if (operations.length > 1) {
            endpoint.setOperationDetail(operations[1]);
        }
    }

}
