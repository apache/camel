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
import org.apache.camel.component.consul.enpoint.ConsulAgentProducer;
import org.apache.camel.component.consul.enpoint.ConsulEventConsumer;
import org.apache.camel.component.consul.enpoint.ConsulEventProducer;
import org.apache.camel.component.consul.enpoint.ConsulKeyValueConsumer;
import org.apache.camel.component.consul.enpoint.ConsulKeyValueProducer;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.jsse.SSLContextParameters;

/**
 * Represents the component that manages {@link ConsulEndpoint}.
 */
public class ConsulComponent extends DefaultComponent {

    @Metadata(label = "advanced")
    private ConsulConfiguration configuration = new ConsulConfiguration();
    
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
        configuration.setCamelContext(getCamelContext());

        setProperties(configuration, parameters);

        return ConsulApiEndpoint.valueOf(remaining).create(remaining, uri, this, configuration);
    }

    // Consul Api Enpoints (see https://www.consul.io/docs/agent/http.html)
    private enum ConsulApiEndpoint {
        kv(ConsulKeyValueProducer::new, ConsulKeyValueConsumer::new),
        event(ConsulEventProducer::new, ConsulEventConsumer::new),
        agent(ConsulAgentProducer::new, null);

        private final Optional<ConsulEndpoint.ProducerFactory> producerFactory;
        private final Optional<ConsulEndpoint.ConsumerFactory> consumerFactory;

        ConsulApiEndpoint(ConsulEndpoint.ProducerFactory producerFactory, ConsulEndpoint.ConsumerFactory consumerFactory) {
            this.producerFactory = Optional.ofNullable(producerFactory);
            this.consumerFactory = Optional.ofNullable(consumerFactory);
        }

        public Endpoint create(String apiEndpoint, String uri, ConsulComponent component, ConsulConfiguration configuration) throws Exception {
            return new ConsulEndpoint(
                apiEndpoint,
                uri,
                component,
                configuration,
                producerFactory,
                consumerFactory);
        }
    }
}
