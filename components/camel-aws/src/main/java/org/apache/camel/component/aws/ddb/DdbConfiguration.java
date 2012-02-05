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

import com.amazonaws.services.dynamodb.AmazonDynamoDB;

/**
 * The AWS SDB component configuration properties
 * 
 */
public class DdbConfiguration {
    
    private String accessKey;
    private String secretKey;
    private AmazonDynamoDB amazonDDBClient;
    private String amazonDdbEndpoint;
    private String tableName;
    private Boolean consistentRead;
    private DdbOperations operation = DdbOperations.PutItem;
    private Long readCapacity;
    private Long writeCapacity;
    private String keyAttributeName;
    private String keyAttributeType;

    public void setAmazonDdbEndpoint(String amazonDdbEndpoint) {
        this.amazonDdbEndpoint = amazonDdbEndpoint;
    }

    public String getAmazonDdbEndpoint() {
        return amazonDdbEndpoint;
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

    public AmazonDynamoDB getAmazonDDBClient() {
        return amazonDDBClient;
    }

    public void setAmazonDDBClient(AmazonDynamoDB amazonDDBClient) {
        this.amazonDDBClient = amazonDDBClient;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public DdbOperations getOperation() {
        return operation;
    }

    public void setOperation(DdbOperations operation) {
        this.operation = operation;
    }

    public Boolean getConsistentRead() {
        return consistentRead;
    }

    public void setConsistentRead(Boolean consistentRead) {
        this.consistentRead = consistentRead;
    }

    public Long getReadCapacity() {
        return readCapacity;
    }

    public void setReadCapacity(Long readCapacity) {
        this.readCapacity = readCapacity;
    }

    public Long getWriteCapacity() {
        return writeCapacity;
    }

    public void setWriteCapacity(Long writeCapacity) {
        this.writeCapacity = writeCapacity;
    }

    public String getKeyAttributeName() {
        return keyAttributeName;
    }

    public void setKeyAttributeName(String keyAttributeName) {
        this.keyAttributeName = keyAttributeName;
    }

    public String getKeyAttributeType() {
        return keyAttributeType;
    }

    public void setKeyAttributeType(String keyAttributeType) {
        this.keyAttributeType = keyAttributeType;
    }
}
