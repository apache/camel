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
package org.apache.camel.component.aws.sqs;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ObjectHelper;

public class SqsComponent extends DefaultComponent {
    
    @Metadata
    private String accessKey;
    @Metadata
    private String secretKey;
    @Metadata
    private String region;
    @Metadata(label = "advanced")    
    private SqsConfiguration configuration;
    
    public SqsComponent() {
        this(null);
    }

    public SqsComponent(CamelContext context) {
        super(context);
        
        this.configuration = new SqsConfiguration();
        registerExtension(new SqsComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        final SqsConfiguration configuration = this.configuration.copy();
        setProperties(configuration, parameters);

        if (remaining == null || remaining.trim().length() == 0) {
            throw new IllegalArgumentException("Queue name must be specified.");
        }

        if (remaining.startsWith("arn:")) {
            String[] parts = remaining.split(":");
            if (parts.length != 6 || !parts[2].equals("sqs")) {
                throw new IllegalArgumentException("Queue arn must be in format arn:aws:sqs:region:account:name.");
            }
            configuration.setRegion(parts[3]);
            configuration.setQueueOwnerAWSAccountId(parts[4]);
            configuration.setQueueName(parts[5]);
        } else {
            configuration.setQueueName(remaining);
        }

        if (ObjectHelper.isEmpty(configuration.getAccessKey())) {
            setAccessKey(accessKey);
        }
        if (ObjectHelper.isEmpty(configuration.getSecretKey())) {
            setSecretKey(secretKey);
        }
        if (ObjectHelper.isEmpty(configuration.getRegion())) {
            setRegion(region);
        }
        
        if (configuration.getAmazonSQSClient() == null && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("AmazonSQSClient or accessKey and secretKey must be specified.");
        }
        
        // Verify that visibilityTimeout is set if extendMessageVisibility is set to true.
        if (configuration.isExtendMessageVisibility() && (configuration.getVisibilityTimeout() == null)) {
            throw new IllegalArgumentException("Extending message visibility (extendMessageVisibility) requires visibilityTimeout to be set on the Endpoint.");
        }
        
        if (configuration.isFifoQueue() && ObjectHelper.isEmpty(configuration.getMessageGroupIdStrategy())) {
            throw new IllegalArgumentException("messageGroupIdStrategy must be set for FIFO queues.");
        }
        
        SqsEndpoint sqsEndpoint = new SqsEndpoint(uri, this, configuration);
        sqsEndpoint.setConsumerProperties(parameters);
        return sqsEndpoint;
    }
    
    public SqsConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The AWS SQS default configuration
     */
    public void setConfiguration(SqsConfiguration configuration) {
        this.configuration = configuration;
    }
    
    public String getAccessKey() {
        return configuration.getAccessKey();
    }

    /**
     * Amazon AWS Access Key
     */
    public void setAccessKey(String accessKey) {
        configuration.setAccessKey(accessKey);
    }

    public String getSecretKey() {
        return configuration.getSecretKey();
    }

    /**
     * Amazon AWS Secret Key
     */
    public void setSecretKey(String secretKey) {
        configuration.setSecretKey(secretKey);
    }
    
    public String getRegion() {
        return configuration.getRegion();
    }

    /**
     * Specify the queue region which could be used with queueOwnerAWSAccountId to build the service URL.
     */
    public void setRegion(String region) {
        configuration.setRegion(region);
    }
}
