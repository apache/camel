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
package org.apache.camel.component.aws2.msk;

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
import software.amazon.awssdk.services.kafka.KafkaClient;

/**
 * For working with Amazon MSK SDK v2.
 */
@Component("aws2-msk")
public class MSK2Component extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(MSK2Component.class);
    
    @Metadata
    private MSK2Configuration configuration = new MSK2Configuration();

    public MSK2Component() {
        this(null);
    }

    public MSK2Component(CamelContext context) {
        super(context);

        registerExtension(new MSK2ComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        MSK2Configuration configuration = this.configuration != null ? this.configuration.copy() : new MSK2Configuration();
        MSK2Endpoint endpoint = new MSK2Endpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        checkAndSetRegistryClient(configuration, endpoint);
        if (configuration.getMskClient() == null && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("Amazon msk client or accessKey and secretKey must be specified");
        }
        return endpoint;
    }

    public MSK2Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Component configuration
     */
    public void setConfiguration(MSK2Configuration configuration) {
        this.configuration = configuration;
    }

    private void checkAndSetRegistryClient(MSK2Configuration configuration, MSK2Endpoint endpoint) {
        if (ObjectHelper.isEmpty(endpoint.getConfiguration().getMskClient())) {
            LOG.debug("Looking for an KafkaClient instance in the registry");
            Set<KafkaClient> clients = getCamelContext().getRegistry().findByType(KafkaClient.class);
            if (clients.size() == 1) {
                LOG.debug("Found exactly one KafkaClient instance in the registry");
                configuration.setMskClient(clients.stream().findFirst().get());
            } else {
                LOG.debug("No KafkaClient instance in the registry");
            }
        } else {
            LOG.debug("KafkaClient instance is already set at endpoint level: skipping the check in the registry");
        }
    }
}
