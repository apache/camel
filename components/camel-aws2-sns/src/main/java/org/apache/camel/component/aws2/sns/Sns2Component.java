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
package org.apache.camel.component.aws2.sns;

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
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

@Component("aws2-sns")
public class Sns2Component extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(Sns2Component.class);
    
    @Metadata
    private Sns2Configuration configuration = new Sns2Configuration();

    public Sns2Component() {
        this(null);
    }

    public Sns2Component(CamelContext context) {
        super(context);

        registerExtension(new Sns2ComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        if (remaining == null || remaining.trim().length() == 0) {
            throw new IllegalArgumentException("Topic name must be specified.");
        }
        Sns2Configuration configuration = this.configuration != null ? this.configuration.copy() : new Sns2Configuration();
        if (remaining.startsWith("arn:")) {
            String[] parts = remaining.split(":");
            if (parts.length != 6 || !parts[2].equals("sns")) {
                throw new IllegalArgumentException("Topic arn must be in format arn:aws:sns:region:account:name.");
            }
            configuration.setTopicArn(remaining);
            configuration.setRegion(Region.of(parts[3]).toString());
        } else {
            configuration.setTopicName(remaining);
        }
        Sns2Endpoint endpoint = new Sns2Endpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        checkAndSetRegistryClient(configuration, endpoint);
        if (configuration.getAmazonSNSClient() == null && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("AmazonSNSClient or accessKey and secretKey must be specified");
        }

        return endpoint;
    }

    public Sns2Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Component configuration
     */
    public void setConfiguration(Sns2Configuration configuration) {
        this.configuration = configuration;
    }

    private void checkAndSetRegistryClient(Sns2Configuration configuration, Sns2Endpoint endpoint) {
        if (ObjectHelper.isEmpty(endpoint.getConfiguration().getAmazonSNSClient())) {
            LOG.debug("Looking for an SnsClient instance in the registry");
            Set<SnsClient> clients = getCamelContext().getRegistry().findByType(SnsClient.class);
            if (clients.size() == 1) {
                LOG.debug("Found exactly one SnsClient instance in the registry");
                configuration.setAmazonSNSClient(clients.stream().findFirst().get());
            } else {
                LOG.debug("No SnsClient instance in the registry");
            }
        } else {
            LOG.debug("SnsClient instance is already set at endpoint level: skipping the check in the registry");
        }
    }
}
