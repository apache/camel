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
package org.apache.camel.component.aws.ddb;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class DdbConfiguration {

    @UriPath @Metadata(required = "true")
    private String tableName;
    @UriParam
    private String accessKey;
    @UriParam
    private String secretKey;
    @UriParam
    private AmazonDynamoDB amazonDDBClient;
    @UriParam
    private String amazonDdbEndpoint;
    @UriParam
    private boolean consistentRead;
    @UriParam(defaultValue = "PutItem")
    private DdbOperations operation = DdbOperations.PutItem;
    @UriParam
    private Long readCapacity;
    @UriParam
    private Long writeCapacity;
    @UriParam
    private String keyAttributeName;
    @UriParam
    private String keyAttributeType;
    @UriParam
    private String proxyHost;
    @UriParam
    private Integer proxyPort;
    @UriParam
    private String region;

    /**
     * The endpoint with which the AWS-DDB client wants to work with.
     */
    public void setAmazonDdbEndpoint(String amazonDdbEndpoint) {
        this.amazonDdbEndpoint = amazonDdbEndpoint;
    }

    public String getAmazonDdbEndpoint() {
        return amazonDdbEndpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    /**
     * Amazon AWS Access Key
     */
    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    /**
     * Amazon AWS Secret Key
     */
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public AmazonDynamoDB getAmazonDDBClient() {
        return amazonDDBClient;
    }

    /**
     * To use the AmazonDynamoDB as the client
     */
    public void setAmazonDDBClient(AmazonDynamoDB amazonDDBClient) {
        this.amazonDDBClient = amazonDDBClient;
    }

    public String getTableName() {
        return tableName;
    }

    /**
     * The name of the table currently worked with.
     */
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public DdbOperations getOperation() {
        return operation;
    }

    /**
     * What operation to perform
     */
    public void setOperation(DdbOperations operation) {
        this.operation = operation;
    }

    public boolean isConsistentRead() {
        return consistentRead;
    }

    /**
     * Determines whether or not strong consistency should be enforced when data is read.
     */
    public void setConsistentRead(boolean consistentRead) {
        this.consistentRead = consistentRead;
    }

    public Long getReadCapacity() {
        return readCapacity;
    }

    /**
     * The provisioned throughput to reserve for reading resources from your table
     */
    public void setReadCapacity(Long readCapacity) {
        this.readCapacity = readCapacity;
    }

    public Long getWriteCapacity() {
        return writeCapacity;
    }

    /**
     * The provisioned throughput to reserved for writing resources to your table
     */
    public void setWriteCapacity(Long writeCapacity) {
        this.writeCapacity = writeCapacity;
    }

    public String getKeyAttributeName() {
        return keyAttributeName;
    }

    /**
     * Attribute name when creating table
     */
    public void setKeyAttributeName(String keyAttributeName) {
        this.keyAttributeName = keyAttributeName;
    }

    public String getKeyAttributeType() {
        return keyAttributeType;
    }

    /**
     * Attribute type when creating table
     */
    public void setKeyAttributeType(String keyAttributeType) {
        this.keyAttributeType = keyAttributeType;
    }
    
    /**
     * To define a proxy host when instantiating the DDB client
     */
    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    /**
     * To define a proxy port when instantiating the DDB client
     */
    public Integer getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    /**
     * The region in which DDB client needs to work
     */
    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }
}
