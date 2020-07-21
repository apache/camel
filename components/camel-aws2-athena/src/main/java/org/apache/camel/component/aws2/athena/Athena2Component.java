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
package org.apache.camel.component.aws2.athena;

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
import software.amazon.awssdk.services.athena.AthenaClient;

/**
 * For working with Amazon Athena SDK v2.
 */
@Component("aws2-athena")
public class Athena2Component extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(Athena2Component.class);

    @Metadata
    private Athena2Configuration configuration = new Athena2Configuration();

    public Athena2Component() {
        this(null);
    }

    public Athena2Component(CamelContext context) {
        super(context);
        registerExtension(new Athena2ComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Athena2Configuration configuration = this.configuration != null ? this.configuration.copy() : new Athena2Configuration();
        Athena2Endpoint endpoint = new Athena2Endpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        checkAndSetRegistryClient(configuration, endpoint);
        if (configuration.getAmazonAthenaClient() == null && (configuration.getAccessKey() == null
            || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("accessKey/secretKey or amazonAthenaClient must be specified");
        }
        return endpoint;
    }

    public Athena2Configuration getConfiguration() {
        return configuration;
    }

    /**
     * The component configuration.
     */
    public void setConfiguration(Athena2Configuration configuration) {
        this.configuration = configuration;
    }

    private void checkAndSetRegistryClient(Athena2Configuration configuration, Athena2Endpoint endpoint) {
        if (ObjectHelper.isEmpty(endpoint.getConfiguration().getAmazonAthenaClient())) {
            LOG.debug("Looking for an AthenaClient instance in the registry");
            Set<AthenaClient> clients = getCamelContext().getRegistry().findByType(AthenaClient.class);
            if (clients.size() == 1) {
                LOG.debug("Found exactly one AthenaClient instance in the registry");
                configuration.setAmazonAthenaClient(clients.stream().findFirst().get());
            } else {
                LOG.debug("No AthenaClient instance in the registry");
            }
        } else {
            LOG.debug("AthenaClient instance is already set at endpoint level: skipping the check in the registry");
        }
    }
}
