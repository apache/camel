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
package org.apache.camel.component.aws2.sqs;

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
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * For working with Amazon SQS SDK v2.
 */
@Component("aws2-sqs")
public class Sqs2Component extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(Sqs2Component.class);

    @Metadata
    private Sqs2Configuration configuration = new Sqs2Configuration();

    public Sqs2Component() {
        this(null);
    }

    public Sqs2Component(CamelContext context) {
        super(context);

        registerExtension(new Sqs2ComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        if (remaining == null || remaining.trim().length() == 0) {
            throw new IllegalArgumentException("Queue name must be specified.");
        }
        Sqs2Configuration configuration = this.configuration != null ? this.configuration.copy() : new Sqs2Configuration();
        if (remaining.startsWith("arn:")) {
            String[] parts = remaining.split(":");
            if (parts.length != 6 || !parts[2].equals("sqs")) {
                throw new IllegalArgumentException("Queue arn must be in format arn:aws:sqs:region:account:name.");
            }
            configuration.setRegion(Region.of(parts[3]).toString());
            configuration.setQueueOwnerAWSAccountId(parts[4]);
            configuration.setQueueName(parts[5]);
        } else {
            configuration.setQueueName(remaining);
        }
        Sqs2Endpoint sqsEndpoint = new Sqs2Endpoint(uri, this, configuration);
        setProperties(sqsEndpoint, parameters);
        checkAndSetRegistryClient(configuration, sqsEndpoint);
        if (configuration.getAmazonSQSClient() == null && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("AmazonSQSClient or accessKey and secretKey must be specified.");
        }

        // Verify that visibilityTimeout is set if extendMessageVisibility is
        // set to true.
        if (configuration.isExtendMessageVisibility() && (configuration.getVisibilityTimeout() == null)) {
            throw new IllegalArgumentException("Extending message visibility (extendMessageVisibility) requires visibilityTimeout to be set on the Endpoint.");
        }
        return sqsEndpoint;
    }

    public Sqs2Configuration getConfiguration() {
        return configuration;
    }

    /**
     * The AWS SQS default configuration
     */
    public void setConfiguration(Sqs2Configuration configuration) {
        this.configuration = configuration;
    }

    private void checkAndSetRegistryClient(Sqs2Configuration configuration, Sqs2Endpoint endpoint) {
        if (ObjectHelper.isEmpty(endpoint.getConfiguration().getAmazonSQSClient())) {
            LOG.debug("Looking for an SqsClient instance in the registry");
            Set<SqsClient> clients = getCamelContext().getRegistry().findByType(SqsClient.class);
            if (clients.size() == 1) {
                LOG.debug("Found exactly one SqsClient instance in the registry");
                configuration.setAmazonSQSClient(clients.stream().findFirst().get());
            } else {
                LOG.debug("No SqsClient instance in the registry");
            }
        } else {
            LOG.debug("SqsClient instance is already set at endpoint level: skipping the check in the registry");
        }
    }
}
