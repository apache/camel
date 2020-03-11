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
package org.apache.camel.component.aws2.ddbstream;

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
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient;

@Component("aws2-ddbstream")
public class Ddb2StreamComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(Ddb2StreamComponent.class);

    @Metadata
    private Ddb2StreamConfiguration configuration = new Ddb2StreamConfiguration();

    public Ddb2StreamComponent() {
        this(null);
    }

    public Ddb2StreamComponent(CamelContext context) {
        super(context);

        registerExtension(new Ddb2StreamComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        if (remaining == null || remaining.trim().length() == 0) {
            throw new IllegalArgumentException("Table name must be specified.");
        }
        Ddb2StreamConfiguration configuration = this.configuration != null ? this.configuration.copy() : new Ddb2StreamConfiguration();
        configuration.setTableName(remaining);
        Ddb2StreamEndpoint endpoint = new Ddb2StreamEndpoint(uri, configuration, this);
        setProperties(endpoint, parameters);
        checkAndSetRegistryClient(configuration, endpoint);
        if (configuration.getAmazonDynamoDbStreamsClient() == null && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("amazonDDBStreamsClient or accessKey and secretKey must be specified");
        }
        return endpoint;
    }

    public Ddb2StreamConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The component configuration
     */
    public void setConfiguration(Ddb2StreamConfiguration configuration) {
        this.configuration = configuration;
    }

    private void checkAndSetRegistryClient(Ddb2StreamConfiguration configuration, Ddb2StreamEndpoint endpoint) {
        if (ObjectHelper.isEmpty(endpoint.getConfiguration().getAmazonDynamoDbStreamsClient())) {
            LOG.debug("Looking for an DynamoDbStreamsClient instance in the registry");
            Set<DynamoDbStreamsClient> clients = getCamelContext().getRegistry().findByType(DynamoDbStreamsClient.class);
            if (clients.size() == 1) {
                LOG.debug("Found exactly one DynamoDbStreamsClient instance in the registry");
                configuration.setAmazonDynamoDbStreamsClient(clients.stream().findFirst().get());
            } else {
                LOG.debug("No DynamoDbStreamsClient instance in the registry");
            }
        } else {
            LOG.debug("DynamoDbStreamsClient instance is already set at endpoint level: skipping the check in the registry");
        }
    }
}
