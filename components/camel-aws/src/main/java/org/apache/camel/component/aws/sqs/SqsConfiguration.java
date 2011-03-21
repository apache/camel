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

import java.util.Collection;

import com.amazonaws.services.sqs.AmazonSQSClient;

/**
 * The AWS SQS component configuration properties
 * 
 */
public class SqsConfiguration {

    // common properties
    private String queueName;
    private AmazonSQSClient amazonSQSClient;
    private String accessKey;
    private String secretKey;
    
    // consumer properties
    private Boolean deleteAfterRead = Boolean.TRUE;
    private Integer visibilityTimeout;
    private Collection<String> attributeNames;
    private Integer defaultVisibilityTimeout;

    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
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

    public Boolean isDeleteAfterRead() {
        return deleteAfterRead;
    }

    public void setDeleteAfterRead(Boolean deleteAfterRead) {
        this.deleteAfterRead = deleteAfterRead;
    }

    public AmazonSQSClient getAmazonSQSClient() {
        return amazonSQSClient;
    }

    public void setAmazonSQSClient(AmazonSQSClient amazonSQSClient) {
        this.amazonSQSClient = amazonSQSClient;
    }
    
    public Integer getVisibilityTimeout() {
        return visibilityTimeout;
    }

    public void setVisibilityTimeout(Integer visibilityTimeout) {
        this.visibilityTimeout = visibilityTimeout;
    }
    
    public Collection<String> getAttributeNames() {
        return attributeNames;
    }

    public void setAttributeNames(Collection<String> attributeNames) {
        this.attributeNames = attributeNames;
    }
    
    public Integer getDefaultVisibilityTimeout() {
        return defaultVisibilityTimeout;
    }

    public void setDefaultVisibilityTimeout(Integer defaultVisibilityTimeout) {
        this.defaultVisibilityTimeout = defaultVisibilityTimeout;
    }

    @Override
    public String toString() {
        return "SqsConfiguration[queueName=" + queueName
            + ", amazonSQSClient=" + amazonSQSClient
            + ", accessKey=" + accessKey
            + ", secretKey=xxxxxxxxxxxxxxx" 
            + ", deleteAfterRead=" + deleteAfterRead
            + ", visibilityTimeout=" + visibilityTimeout
            + ", attributeNames=" + attributeNames
            + ", defaultVisibilityTimeout=" + defaultVisibilityTimeout
            + "]";
    }
}