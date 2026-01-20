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

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.aws2.ddbstream.client.Ddb2StreamClientFactory;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient;

/**
 * Receive messages from AWS DynamoDB Stream.
 */
@UriEndpoint(firstVersion = "3.1.0", scheme = "aws2-ddbstream", title = "AWS DynamoDB Streams", consumerOnly = true,
             syntax = "aws2-ddbstream:tableName", category = { Category.CLOUD, Category.MESSAGING })
public class Ddb2StreamEndpoint extends ScheduledPollEndpoint implements EndpointServiceLocation {

    @UriParam
    Ddb2StreamConfiguration configuration;

    private DynamoDbStreamsClient ddbStreamClient;

    public Ddb2StreamEndpoint(String uri, Ddb2StreamConfiguration configuration, Ddb2StreamComponent component) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Ddb2StreamConsumer consumer = new Ddb2StreamConsumer(this, processor);
        consumer.setSchedulerProperties(consumer.getEndpoint().getSchedulerProperties());
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        ddbStreamClient = configuration.getAmazonDynamoDbStreamsClient() != null
                ? configuration.getAmazonDynamoDbStreamsClient()
                : Ddb2StreamClientFactory.getDynamoDBStreamClient(configuration);
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getAmazonDynamoDbStreamsClient())) {
            if (ddbStreamClient != null) {
                ddbStreamClient.close();
            }
        }
        super.doStop();
    }

    @Override
    public Ddb2StreamComponent getComponent() {
        return (Ddb2StreamComponent) super.getComponent();
    }

    public Ddb2StreamConfiguration getConfiguration() {
        return configuration;
    }

    public DynamoDbStreamsClient getClient() {
        return ddbStreamClient;
    }

    @Override
    public String toString() {
        return "DdbStreamEndpoint{" + "tableName=" + configuration.getTableName()
               + ", amazonDynamoDbStreamsClient=[redacted], maxResultsPerRequest="
               + configuration.getMaxResultsPerRequest() + ", streamIteratorType=" + configuration.getStreamIteratorType()
               + ", uri=" + getEndpointUri() + '}';
    }

    @Override
    public String getServiceUrl() {
        if (!configuration.isOverrideEndpoint()) {
            if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
                return configuration.getRegion();
            }
        } else if (ObjectHelper.isNotEmpty(configuration.getUriEndpointOverride())) {
            return configuration.getUriEndpointOverride();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "dynamodb-stream";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (configuration.getTableName() != null) {
            return Map.of("table", configuration.getTableName());
        }
        return null;
    }
}
