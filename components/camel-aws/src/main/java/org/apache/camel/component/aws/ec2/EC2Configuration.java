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
package org.apache.camel.component.aws.ec2;

import com.amazonaws.services.ec2.AmazonEC2Client;

import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class EC2Configuration {
    
    @UriParam
    private AmazonEC2Client amazonEc2Client;
    @UriParam
    private String accessKey;
    @UriParam
    private String secretKey;
    @UriParam
    private String amazonEc2Endpoint;
    @UriParam
    private EC2Operations operation;
    
    public AmazonEC2Client getAmazonEc2Client() {
        return amazonEc2Client;
    }
    
    public void setAmazonEc2Client(AmazonEC2Client amazonEc2Client) {
        this.amazonEc2Client = amazonEc2Client;
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
    
    public String getAmazonEc2Endpoint() {
        return amazonEc2Endpoint;
    }
    
    public void setAmazonEc2Endpoint(String amazonEc2Endpoint) {
        this.amazonEc2Endpoint = amazonEc2Endpoint;
    }

    public EC2Operations getOperation() {
        return operation;
    }

    public void setOperation(EC2Operations operation) {
        this.operation = operation;
    } 
}
