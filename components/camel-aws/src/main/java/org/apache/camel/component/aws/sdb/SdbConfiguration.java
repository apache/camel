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
package org.apache.camel.component.aws.sdb;

import com.amazonaws.services.simpledb.AmazonSimpleDB;

public class SdbConfiguration {
    private String accessKey;
    private String secretKey;
    private AmazonSimpleDB amazonSdbClient;
    private String amazonSdbEndpoint;
    private String domainName;
    private String operation;

    public void setAmazonSdbEndpoint(String amazonSdbEndpoint) {
        this.amazonSdbEndpoint = amazonSdbEndpoint;
    }

    public String getAmazonSdbEndpoint() {
        return amazonSdbEndpoint;
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

    public AmazonSimpleDB getAmazonSdbClient() {
        return amazonSdbClient;
    }

    public void setAmazonSdbClient(AmazonSimpleDB amazonSdbClient) {
        this.amazonSdbClient = amazonSdbClient;
    }

    public String getDomainName() {
        return domainName;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }
}
