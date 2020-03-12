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
package org.apache.camel.component.aws2.ses;

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
import software.amazon.awssdk.services.ses.SesClient;

/**
 * For working with Amazon ECS SDK v2.
 */
@Component("aws2-ses")
public class Ses2Component extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(Ses2Component.class);
    
    @Metadata
    private Ses2Configuration configuration = new Ses2Configuration();

    public Ses2Component() {
        this(null);
    }

    public Ses2Component(CamelContext context) {
        super(context);

        registerExtension(new Ses2ComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        if (remaining == null || remaining.trim().length() == 0) {
            throw new IllegalArgumentException("From must be specified.");
        }
        Ses2Configuration configuration = this.configuration != null ? this.configuration.copy() : new Ses2Configuration();
        configuration.setFrom(remaining);
        Ses2Endpoint endpoint = new Ses2Endpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        checkAndSetRegistryClient(configuration, endpoint);
        if (configuration.getAmazonSESClient() == null && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("AmazonSESClient or accessKey and secretKey must be specified");
        }

        return endpoint;
    }

    public Ses2Configuration getConfiguration() {
        return configuration;
    }

    /**
     * component configuration
     */
    public void setConfiguration(Ses2Configuration configuration) {
        this.configuration = configuration;
    }

    private void checkAndSetRegistryClient(Ses2Configuration configuration, Ses2Endpoint endpoint) {
        if (ObjectHelper.isEmpty(endpoint.getConfiguration().getAmazonSESClient())) {
            LOG.debug("Looking for an SesClient instance in the registry");
            Set<SesClient> clients = getCamelContext().getRegistry().findByType(SesClient.class);
            if (clients.size() == 1) {
                LOG.debug("Found exactly one SesClient instance in the registry");
                configuration.setAmazonSESClient(clients.stream().findFirst().get());
            } else {
                LOG.debug("No SesClient instance in the registry");
            }
        } else {
            LOG.debug("SesClient instance is already set at endpoint level: skipping the check in the registry");
        }
    }
}
