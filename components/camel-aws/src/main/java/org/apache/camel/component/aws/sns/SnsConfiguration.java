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
package org.apache.camel.component.aws.sns;

/**
 * The AWS SNS component configuration properties
 * 
 */
import com.amazonaws.services.sns.AmazonSNS;

public class SnsConfiguration implements Cloneable {

    // Common properties
    private String topicName;
    private AmazonSNS amazonSNSClient;
    private String accessKey;
    private String secretKey;
    private String amazonSNSEndpoint;

    // Producer only properties
    private String subject;
    private String topicArn;
    private String policy;

    public void setAmazonSNSEndpoint(String awsSNSEndpoint) {
        this.amazonSNSEndpoint = awsSNSEndpoint;
    }
    
    public String getAmazonSNSEndpoint() {
        return amazonSNSEndpoint;
    }
    
    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getTopicArn() {
        return topicArn;
    }

    public void setTopicArn(String topicArn) {
        this.topicArn = topicArn;
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

    public AmazonSNS getAmazonSNSClient() {
        return amazonSNSClient;
    }

    public void setAmazonSNSClient(AmazonSNS amazonSNSClient) {
        this.amazonSNSClient = amazonSNSClient;
    }

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }
    
    public String getPolicy() {
        return policy;
    }

    public void setPolicy(String policy) {
        this.policy = policy;
    }
    
    @Override
    public String toString() {
        return "SnsConfiguration[topicName=" + topicName
            + ", amazonSNSClient=" + amazonSNSClient
            + ", accessKey=" + accessKey
            + ", secretKey=xxxxxxxxxxxxxxx" 
            + ", subject=" + subject
            + ", topicArn=" + topicArn
            + ", policy=" + policy
            + "]";
    }
}