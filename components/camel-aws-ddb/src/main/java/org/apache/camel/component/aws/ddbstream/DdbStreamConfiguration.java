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
package org.apache.camel.component.aws.ddbstream;

import com.amazonaws.Protocol;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreams;
import com.amazonaws.services.dynamodbv2.model.ShardIteratorType;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class DdbStreamConfiguration implements Cloneable {
    
    @UriPath(label = "consumer", description = "Name of the dynamodb table")
    @Metadata(required = true)
    private String tableName;
    
    @UriParam(label = "security", secret = true, description = "Amazon AWS Access Key")
    private String accessKey;
    @UriParam(label = "security", secret = true, description = "Amazon AWS Secret Key")
    private String secretKey;
    @UriParam(description = "The region in which DDBStreams client needs to work")
    private String region;

    @UriParam(label = "consumer", description = "Amazon DynamoDB client to use for all requests for this endpoint")
    private AmazonDynamoDBStreams amazonDynamoDbStreamsClient;

    @UriParam(label = "consumer", description = "Maximum number of records that will be fetched in each poll")
    private int maxResultsPerRequest = 100;

    @UriParam(label = "consumer", description = "Defines where in the DynaboDB stream"
            + " to start getting records. Note that using TRIM_HORIZON can cause a"
            + " significant delay before the stream has caught up to real-time."
            + " if {AT,AFTER}_SEQUENCE_NUMBER are used, then a sequenceNumberProvider"
            + " MUST be supplied.",
            defaultValue = "LATEST")
    private ShardIteratorType iteratorType = ShardIteratorType.LATEST;

    @UriParam(label = "consumer", description = "Provider for the sequence number when"
            + " using one of the two ShardIteratorType.{AT,AFTER}_SEQUENCE_NUMBER"
            + " iterator types. Can be a registry reference or a literal sequence number.")
    private SequenceNumberProvider sequenceNumberProvider;
    @UriParam(enums = "HTTP,HTTPS", defaultValue = "HTTPS", description = "To define a proxy protocol when instantiating the DDBStreams client")
    private Protocol proxyProtocol = Protocol.HTTPS;
    @UriParam(description = "To define a proxy host when instantiating the DDBStreams client")
    private String proxyHost;
    @UriParam(description = "To define a proxy port when instantiating the DDBStreams client")
    private Integer proxyPort;
    
    public AmazonDynamoDBStreams getAmazonDynamoDbStreamsClient() {
        return amazonDynamoDbStreamsClient;
    }

    public void setAmazonDynamoDbStreamsClient(AmazonDynamoDBStreams amazonDynamoDbStreamsClient) {
        this.amazonDynamoDbStreamsClient = amazonDynamoDbStreamsClient;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
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

    public SequenceNumberProvider getSequenceNumberProvider() {
        return sequenceNumberProvider;
    }

    public void setSequenceNumberProvider(SequenceNumberProvider sequenceNumberProvider) {
        this.sequenceNumberProvider = sequenceNumberProvider;
    }
    
    public Protocol getProxyProtocol() {
        return proxyProtocol;
    }

    public void setProxyProtocol(Protocol proxyProtocol) {
        this.proxyProtocol = proxyProtocol;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }
    
    // *************************************************
    //
    // *************************************************

    public DdbStreamConfiguration copy() {
        try {
            return (DdbStreamConfiguration)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
