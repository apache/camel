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
package org.apache.camel.component.aws2.mq;

import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.mq.MqClient;

/**
 * For working with Amazon MQ SDK v2.
 */
@Component("aws2-mq")
public class MQ2Component extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(MQ2Component.class);
    
    @Metadata
    private MQ2Configuration configuration = new MQ2Configuration();

    public MQ2Component() {
        this(null);
    }

    public MQ2Component(CamelContext context) {
        super(context);

        registerExtension(new MQ2ComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        MQ2Configuration configuration = this.configuration != null ? this.configuration.copy() : new MQ2Configuration();
        MQ2Endpoint endpoint = new MQ2Endpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        checkAndSetRegistryClient(configuration, endpoint);
        if (configuration.getAmazonMqClient() == null && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("amazonMQClient or accessKey and secretKey must be specified");
        }

        return endpoint;
    }

    public MQ2Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Component configuration
     */
    public void setConfiguration(MQ2Configuration configuration) {
        this.configuration = configuration;
    }

    private void checkAndSetRegistryClient(MQ2Configuration configuration, MQ2Endpoint endpoint) {
        if (ObjectHelper.isEmpty(endpoint.getConfiguration().getAmazonMqClient())) {
            LOG.debug("Looking for an MqClient instance in the registry");
            Set<MqClient> clients = getCamelContext().getRegistry().findByType(MqClient.class);
            if (clients.size() == 1) {
                LOG.debug("Found exactly one MqClient instance in the registry");
                configuration.setAmazonMqClient(clients.stream().findFirst().get());
            } else {
                LOG.debug("No MqClient instance in the registry");
            }
        } else {
            LOG.debug("MqClient instance is already set at endpoint level: skipping the check in the registry");
        }
    }
}
