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
package org.apache.camel.component.aws.ddbstream;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreams;
import com.amazonaws.services.dynamodbv2.model.Record;
import com.amazonaws.services.dynamodbv2.model.ShardIteratorType;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

@UriEndpoint(scheme = "aws-ddbstream", title = "AWS DynamoDB Streams",
        consumerOnly = true, syntax = "aws-ddbstream:tableName",
        consumerClass = DdbStreamConsumer.class, label = "cloud,messaging,streams")
public class DdbStreamEndpoint extends ScheduledPollEndpoint {

    @UriPath(label = "consumer", description = "Name of the dynamodb table")
    @Metadata(required = "true")
    private String tableName;

    // For now, always assume that we've been supplied a client in the Camel registry.
    @UriParam(label = "consumer", description = "Amazon DynamoDB client to use for all requests for this endpoint")
    @Metadata(required = "true")
    private AmazonDynamoDBStreams amazonDynamoDbStreamsClient;

    @UriParam(label = "consumer", description = "Maximum number of records that will be fetched in each poll")
    private int maxResultsPerRequest = 100;

    @UriParam(label = "consumer", description = "Defines where in the DynaboDB stream"
            + " to start getting records. Note that using TRIM_HORIZON can cause a"
            + " significant delay before the stream has caught up to real-time."
            + " Currently only LATEST and TRIM_HORIZON are supported.",
            defaultValue = "LATEST")
    private ShardIteratorType iteratorType = ShardIteratorType.LATEST;
    // TODO add the ability to use ShardIteratorType.{AT,AFTER}_SEQUENCE_NUMBER
    // by specifying either a sequence number itself or a bean to fetch the
    // sequence number from persistant storage or somewhere else.
    // This can be done by having the type of the parameter an interface
    // and supplying a default implementation and a converter from a long/String
    // to an instance of this interface.
    // Note that the shard list needs to have the ability to start at the shard
    // that includes the supplied sequence number

    public DdbStreamEndpoint(String uri, String tableName, DdbStreamComponent component) {
        super(uri, component);
        this.tableName = tableName;
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        DdbStreamConsumer consumer = new DdbStreamConsumer(this, processor);
        consumer.setSchedulerProperties(consumer.getEndpoint().getSchedulerProperties());
        return consumer;
    }

    Exchange createExchange(Record record) {
        Exchange ex = super.createExchange();
        ex.getIn().setBody(record, Record.class);

        return ex;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public String toString() {
        return "DdbStreamEndpoint{"
                + "tableName=" + tableName
                + ", amazonDynamoDbStreamsClient=[redacted], maxResultsPerRequest=" + maxResultsPerRequest
                + ", iteratorType="
                + iteratorType + ", uri=" + getEndpointUri()
                + '}';
    }

    AmazonDynamoDBStreams getClient() {
        return amazonDynamoDbStreamsClient;
    }

    public AmazonDynamoDBStreams getAmazonDynamoDBStreamsClient() {
        return amazonDynamoDbStreamsClient;
    }

    public void setAmazonDynamoDbStreamsClient(AmazonDynamoDBStreams amazonDynamoDbStreamsClient) {
        this.amazonDynamoDbStreamsClient = amazonDynamoDbStreamsClient;
    }

    public int getMaxResultsPerRequest() {
        return maxResultsPerRequest;
    }

    public void setMaxResultsPerRequest(int maxResultsPerRequest) {
        this.maxResultsPerRequest = maxResultsPerRequest;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public ShardIteratorType getIteratorType() {
        return iteratorType;
    }

    public void setIteratorType(ShardIteratorType iteratorType) {
        this.iteratorType = iteratorType;
    }
    
}
