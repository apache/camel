/**
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
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.jsse.SSLContextParameters;

/**
 * Represents the component that manages {@link ConsulEndpoint}.
 */
public class ConsulComponent extends DefaultComponent implements SSLContextParametersAware {

    @Metadata(label = "advanced")
    private ConsulConfiguration configuration = new ConsulConfiguration();
    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;
    
    public ConsulComponent() {
        super();
    }

    public ConsulComponent(CamelContext context) {
        super(context);
    }

    // ************************************
    // Options
    // ************************************


    public String getUrl() {
        return this.configuration.getUrl();
    }

    /**
     * The Consul agent URL
     */
    public void setUrl(String url) {
        this.configuration.setUrl(url);
    }

    public String getDatacenter() {
        return configuration.getDatacenter();
    }

    /**
     * The data center
     * @param datacenter
     */
    public void setDatacenter(String datacenter) {
        configuration.setDatacenter(datacenter);
    }

    public SSLContextParameters getSslContextParameters() {
        return configuration.getSslContextParameters();
    }

    /**
     * SSL configuration using an org.apache.camel.util.jsse.SSLContextParameters
     * instance.
     * @param sslContextParameters
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        configuration.setSslContextParameters(sslContextParameters);
    }

    @Override
    public boolean isUseGlobalSslContextParameters() {
        return this.useGlobalSslContextParameters;
    }

    /**
     * Enable usage of global SSL context parameters.
     */
    @Override
    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
    }

    public String getAclToken() {
        return configuration.getAclToken();
    }

    /**
     * Sets the ACL token to be used with Consul
     * @param aclToken
     */
    public void setAclToken(String aclToken) {
        configuration.setAclToken(aclToken);
    }

    public String getUserName() {
        return configuration.getUserName();
    }

    /**
     * Sets the username to be used for basic authentication
     * @param userName
     */
    public void setUserName(String userName) {
        configuration.setUserName(userName);
    }

    public String getPassword() {
        return configuration.getPassword();
    }

    /**
     * Sets the password to be used for basic authentication
     * @param password
     */
    public void setPassword(String password) {
        configuration.setPassword(password);
    }

    public ConsulConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Sets the common configuration shared among endpoints
     */
    public void setConfiguration(ConsulConfiguration configuration) {
        this.configuration = configuration;
    }


    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        ConsulConfiguration configuration = Optional.ofNullable(this.configuration).orElseGet(ConsulConfiguration::new).copy();

        // using global ssl context parameters if set
        if (configuration.getSslContextParameters() == null) {
            configuration.setSslContextParameters(retrieveGlobalSslContextParameters());
        }

        setProperties(configuration, parameters);

        switch (remaining) {
        case "kv":
            return new ConsulEndpoint(
                remaining, uri, this, configuration, Optional.of(ConsulKeyValueProducer::new), Optional.of(ConsulKeyValueConsumer::new)
            );
        case "event":
            return new ConsulEndpoint(
                remaining, uri, this, configuration, Optional.of(ConsulEventProducer::new), Optional.of(ConsulEventConsumer::new)
            );
        case "agent":
            return new ConsulEndpoint(
                remaining, uri, this, configuration, Optional.of(ConsulAgentProducer::new), Optional.empty()
            );
        case "coordinates":
            return new ConsulEndpoint(
                remaining, uri, this, configuration, Optional.of(ConsulCoordinatesProducer::new), Optional.empty()
            );
        case "health":
            return new ConsulEndpoint(
                remaining, uri, this, configuration, Optional.of(ConsulHealthProducer::new), Optional.empty()
            );
        case "status":
            return new ConsulEndpoint(
                remaining, uri, this, configuration, Optional.of(ConsulStatusProducer::new), Optional.empty()
            );
        case "preparedQuery":
            return new ConsulEndpoint(
                remaining, uri, this, configuration, Optional.of(ConsulPreparedQueryProducer::new), Optional.empty()
            );
        case "catalog":
            return new ConsulEndpoint(
                remaining, uri, this, configuration, Optional.of(ConsulCatalogProducer::new), Optional.empty()
            );
        case "session":
            return new ConsulEndpoint(
                remaining, uri, this, configuration, Optional.of(ConsulSessionProducer::new), Optional.empty()
            );
        default:
            throw new IllegalArgumentException("Unknown apiEndpoint: " + remaining);
        }
    }
}
