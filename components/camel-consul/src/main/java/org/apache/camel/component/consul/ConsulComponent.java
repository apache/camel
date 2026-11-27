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
package org.apache.camel.component.consul;

import java.util.Map;
import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.component.consul.endpoint.ConsulAgentProducer;
import org.apache.camel.component.consul.endpoint.ConsulCatalogProducer;
import org.apache.camel.component.consul.endpoint.ConsulCoordinatesProducer;
import org.apache.camel.component.consul.endpoint.ConsulEventConsumer;
import org.apache.camel.component.consul.endpoint.ConsulEventProducer;
import org.apache.camel.component.consul.endpoint.ConsulHealthProducer;
import org.apache.camel.component.consul.endpoint.ConsulKeyValueConsumer;
import org.apache.camel.component.consul.endpoint.ConsulKeyValueProducer;
import org.apache.camel.component.consul.endpoint.ConsulPreparedQueryProducer;
import org.apache.camel.component.consul.endpoint.ConsulSessionProducer;
import org.apache.camel.component.consul.endpoint.ConsulStatusProducer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

@Component("consul")
public class ConsulComponent extends DefaultComponent implements SSLContextParametersAware {

    @Metadata(label = "advanced",
              description = "The shared configuration of the endpoints that this component creates. Each endpoint gets a"
                            + " copy, and the endpoint options override it.")
    private ConsulConfiguration configuration = new ConsulConfiguration();
    @Metadata(label = "security",
              description = "Whether to use the global SSL context parameters of the CamelContext for the endpoints that"
                            + " have no sslContextParameters.")
    private boolean useGlobalSslContextParameters;

    public ConsulComponent() {
    }

    public ConsulComponent(CamelContext context) {
        super(context);
    }

    // ************************************
    // Options
    // ************************************

    @Override
    public boolean isUseGlobalSslContextParameters() {
        return this.useGlobalSslContextParameters;
    }

    @Override
    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
    }

    public ConsulConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(ConsulConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        ConsulConfiguration configuration = Optional.ofNullable(this.configuration).orElseGet(ConsulConfiguration::new).copy();

        ConsulEndpoint endpoint;
        switch (remaining) {
            case "kv":
                endpoint = new ConsulEndpoint(
                        remaining, uri, this, configuration, Optional.of(ConsulKeyValueProducer::new),
                        Optional.of(ConsulKeyValueConsumer::new));
                break;
            case "event":
                endpoint = new ConsulEndpoint(
                        remaining, uri, this, configuration, Optional.of(ConsulEventProducer::new),
                        Optional.of(ConsulEventConsumer::new));
                break;
            case "agent":
                endpoint = new ConsulEndpoint(
                        remaining, uri, this, configuration, Optional.of(ConsulAgentProducer::new), Optional.empty());
                break;
            case "coordinates":
                endpoint = new ConsulEndpoint(
                        remaining, uri, this, configuration, Optional.of(ConsulCoordinatesProducer::new), Optional.empty());
                break;
            case "health":
                endpoint = new ConsulEndpoint(
                        remaining, uri, this, configuration, Optional.of(ConsulHealthProducer::new), Optional.empty());
                break;
            case "status":
                endpoint = new ConsulEndpoint(
                        remaining, uri, this, configuration, Optional.of(ConsulStatusProducer::new), Optional.empty());
                break;
            case "preparedQuery":
                endpoint = new ConsulEndpoint(
                        remaining, uri, this, configuration, Optional.of(ConsulPreparedQueryProducer::new), Optional.empty());
                break;
            case "catalog":
                endpoint = new ConsulEndpoint(
                        remaining, uri, this, configuration, Optional.of(ConsulCatalogProducer::new), Optional.empty());
                break;
            case "session":
                endpoint = new ConsulEndpoint(
                        remaining, uri, this, configuration, Optional.of(ConsulSessionProducer::new), Optional.empty());
                break;
            default:
                endpoint = new ConsulEndpoint(
                        remaining, uri, this, configuration, Optional.of(ConsulKeyValueProducer::new),
                        Optional.of(ConsulKeyValueConsumer::new));
        }

        setProperties(endpoint, parameters);
        // using global ssl context parameters if set
        if (configuration.getSslContextParameters() == null) {
            configuration.setSslContextParameters(retrieveGlobalSslContextParameters());
        }
        return endpoint;
    }

}
