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
package org.apache.camel.component.aws2.sts;

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
import software.amazon.awssdk.services.sts.StsClient;

/**
 * For working with Amazon STS SDK v2.
 */
@Component("aws2-sts")
public class STS2Component extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(STS2Component.class);

    @Metadata
    private STS2Configuration configuration = new STS2Configuration();

    public STS2Component() {
        this(null);
    }

    public STS2Component(CamelContext context) {
        super(context);

        registerExtension(new STS2ComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        STS2Configuration configuration = this.configuration != null ? this.configuration.copy() : new STS2Configuration();
        STS2Endpoint endpoint = new STS2Endpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        if (endpoint.getConfiguration().isAutoDiscoverClient()) {
            checkAndSetRegistryClient(configuration, endpoint);
        }
        if (configuration.getStsClient() == null
                && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("Amazon STS client or accessKey and secretKey must be specified");
        }

        return endpoint;
    }

    public STS2Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Component configuration
     */
    public void setConfiguration(STS2Configuration configuration) {
        this.configuration = configuration;
    }

    private void checkAndSetRegistryClient(STS2Configuration configuration, STS2Endpoint endpoint) {
        if (ObjectHelper.isEmpty(endpoint.getConfiguration().getStsClient())) {
            LOG.debug("Looking for an StsClient instance in the registry");
            Set<StsClient> clients = getCamelContext().getRegistry().findByType(StsClient.class);
            if (clients.size() == 1) {
                LOG.debug("Found exactly one StsClient instance in the registry");
                configuration.setStsClient(clients.stream().findFirst().get());
            } else {
                LOG.debug("No StsClient instance in the registry");
            }
        } else {
            LOG.debug("StsClient instance is already set at endpoint level: skipping the check in the registry");
        }
    }
}
