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
package org.apache.camel.component.aws.kinesis;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.model.ShardIteratorType;

@UriParams
public class KinesisConfiguration {
    
    @UriPath(description = "Name of the stream")
    @Metadata(required = "true")
    private String streamName;
    @UriParam(description = "Amazon Kinesis client to use for all requests for this endpoint")
    @Metadata(required = "true")
    private AmazonKinesis amazonKinesisClient;
    @UriParam(label = "consumer", description = "Maximum number of records that will be fetched in each poll", defaultValue = "1")
    private int maxResultsPerRequest = 1;
    @UriParam(label = "consumer", description = "Defines where in the Kinesis stream to start getting records", defaultValue = "TRIM_HORIZON")
    private ShardIteratorType iteratorType = ShardIteratorType.TRIM_HORIZON;
    @UriParam(label = "consumer", description = "Defines which shardId in the Kinesis stream to get records from")
    private String shardId = "";
    @UriParam(label = "consumer", description = "The sequence number to start polling from. Required if iteratorType is set to AFTER_SEQUENCE_NUMBER or AT_SEQUENCE_NUMBER")
    private String sequenceNumber = "";
    @UriParam(label = "consumer", defaultValue = "ignore", description = "Define what will be the behavior in case of shard closed. Possible value are ignore, silent and fail."
                                                                         + "In case of ignore a message will be logged and the consumer will restart from the beginning,"
                                                                         + "in case of silent there will be no logging and the consumer will start from the beginning,"
                                                                         + "in case of fail a ReachedClosedStateException will be raised")
    private KinesisShardClosedStrategyEnum shardClosed;
    
    // required for injection.
    public AmazonKinesis getAmazonKinesisClient() {
        return amazonKinesisClient;
    }

    public void setAmazonKinesisClient(AmazonKinesis amazonKinesisClient) {
        this.amazonKinesisClient = amazonKinesisClient;
    }

    public int getMaxResultsPerRequest() {
        return maxResultsPerRequest;
    }

    public void setMaxResultsPerRequest(int maxResultsPerRequest) {
        this.maxResultsPerRequest = maxResultsPerRequest;
    }

    public String getStreamName() {
        return streamName;
    }

    public void setStreamName(String streamName) {
        this.streamName = streamName;
    }

    public ShardIteratorType getIteratorType() {
        return iteratorType;
    }

    public void setIteratorType(ShardIteratorType iteratorType) {
        this.iteratorType = iteratorType;
    }

    public String getShardId() {
        return shardId;
    }

    public void setShardId(String shardId) {
        this.shardId = shardId;
    }

    public String getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(String sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public KinesisShardClosedStrategyEnum getShardClosed() {
        return shardClosed;
    }

    public void setShardClosed(KinesisShardClosedStrategyEnum shardClosed) {
        this.shardClosed = shardClosed;
    }

}
