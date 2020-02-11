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
package org.apache.camel.component.aws2.ddb;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@UriParams
public class Ddb2Configuration implements Cloneable {

    @UriPath
    @Metadata(required = true)
    private String tableName;
    @UriParam(label = "security", secret = true)
    private String accessKey;
    @UriParam(label = "security", secret = true)
    private String secretKey;
    @UriParam
    private DynamoDbClient amazonDDBClient;
    @UriParam
    private boolean consistentRead;
    @UriParam(defaultValue = "PutItem")
    private Ddb2Operations operation = Ddb2Operations.PutItem;
    @UriParam
    private Long readCapacity;
    @UriParam
    private Long writeCapacity;
    @UriParam
    private String keyAttributeName;
    @UriParam
    private String keyAttributeType;
    @UriParam(enums = "HTTP,HTTPS", defaultValue = "HTTPS")
    private Protocol proxyProtocol = Protocol.HTTPS;
    @UriParam
    private String proxyHost;
    @UriParam
    private Integer proxyPort;
    @UriParam
    private String region;

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

    public DynamoDbClient getAmazonDDBClient() {
        return amazonDDBClient;
    }

    /**
     * To use the AmazonDynamoDB as the client
     */
    public void setAmazonDDBClient(DynamoDbClient amazonDDBClient) {
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

    public Ddb2Operations getOperation() {
        return operation;
    }

    /**
     * What operation to perform
     */
    public void setOperation(Ddb2Operations operation) {
        this.operation = operation;
    }

    public boolean isConsistentRead() {
        return consistentRead;
    }

    /**
     * Determines whether or not strong consistency should be enforced when data
     * is read.
     */
    public void setConsistentRead(boolean consistentRead) {
        this.consistentRead = consistentRead;
    }

    public Long getReadCapacity() {
        return readCapacity;
    }

    /**
     * The provisioned throughput to reserve for reading resources from your
     * table
     */
    public void setReadCapacity(Long readCapacity) {
        this.readCapacity = readCapacity;
    }

    public Long getWriteCapacity() {
        return writeCapacity;
    }

    /**
     * The provisioned throughput to reserved for writing resources to your
     * table
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

    public Protocol getProxyProtocol() {
        return proxyProtocol;
    }

    /**
     * To define a proxy protocol when instantiating the DDB client
     */
    public void setProxyProtocol(Protocol proxyProtocol) {
        this.proxyProtocol = proxyProtocol;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * To define a proxy host when instantiating the DDB client
     */
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    /**
     * The region in which DynamoDB client needs to work. When using this
     * parameter, the configuration will expect the lowercase name of the
     * region (for example ap-east-1) You'll need to use the name
     * Region.EU_WEST_1.id()
     */
    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getRegion() {
        return region;
    }

    /**
     * The region in which DDB client needs to work
     */
    public void setRegion(String region) {
        this.region = region;
    }

    // *************************************************
    //
    // *************************************************

    public Ddb2Configuration copy() {
        try {
            return (Ddb2Configuration)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
