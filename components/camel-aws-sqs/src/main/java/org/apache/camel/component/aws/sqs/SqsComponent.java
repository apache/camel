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
package org.apache.camel.component.aws.sqs;

import java.util.Map;
import java.util.Set;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.sqs.AmazonSQS;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

@Component("aws-sqs")
public class SqsComponent extends DefaultComponent {

    @Metadata
    private SqsConfiguration configuration = new SqsConfiguration();

    public SqsComponent() {
        this(null);
    }

    public SqsComponent(CamelContext context) {
        super(context);

        registerExtension(new SqsComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        if (remaining == null || remaining.trim().length() == 0) {
            throw new IllegalArgumentException("Queue name must be specified.");
        }
        SqsConfiguration configuration = this.configuration != null ? this.configuration.copy() : new SqsConfiguration();
        if (remaining.startsWith("arn:")) {
            String[] parts = remaining.split(":");
            if (parts.length != 6 || !parts[2].equals("sqs")) {
                throw new IllegalArgumentException("Queue arn must be in format arn:aws:sqs:region:account:name.");
            }
            configuration.setRegion(Regions.fromName(parts[3]).toString());
            configuration.setQueueOwnerAWSAccountId(parts[4]);
            configuration.setQueueName(parts[5]);
        } else {
            configuration.setQueueName(remaining);
        }
        SqsEndpoint sqsEndpoint = new SqsEndpoint(uri, this, configuration);
        setProperties(sqsEndpoint, parameters);
        if (sqsEndpoint.getConfiguration().isAutoDiscoverClient()) {
            checkAndSetRegistryClient(configuration);
        }
        if (configuration.getAmazonSQSClient() == null
                && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("AmazonSQSClient or accessKey and secretKey must be specified.");
        }

        // Verify that visibilityTimeout is set if extendMessageVisibility is
        // set to true.
        if (configuration.isExtendMessageVisibility() && (configuration.getVisibilityTimeout() == null)) {
            throw new IllegalArgumentException(
                    "Extending message visibility (extendMessageVisibility) requires visibilityTimeout to be set on the Endpoint.");
        }
        return sqsEndpoint;
    }

    public SqsConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The component configuration
     */
    public void setConfiguration(SqsConfiguration configuration) {
        this.configuration = configuration;
    }

    private void checkAndSetRegistryClient(SqsConfiguration configuration) {
        Set<AmazonSQS> clients = getCamelContext().getRegistry().findByType(AmazonSQS.class);
        if (clients.size() == 1) {
            configuration.setAmazonSQSClient(clients.stream().findFirst().get());
        }
    }
}
