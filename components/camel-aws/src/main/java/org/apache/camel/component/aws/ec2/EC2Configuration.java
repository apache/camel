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

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class EC2Configuration implements Cloneable {

    @UriPath(description = "Logical name") @Metadata(required = "true")
    private String label;
    @UriParam(label = "producer")
    private AmazonEC2Client amazonEc2Client;
    @UriParam(label = "producer", secret = true)
    private String accessKey;
    @UriParam(label = "producer", secret = true)
    private String secretKey;
    @UriParam(label = "producer")
    private String amazonEc2Endpoint;
    @UriParam(label = "producer")
    @Metadata(required = "true")
    private EC2Operations operation;
    @UriParam(label = "producer")
    private String proxyHost;
    @UriParam(label = "producer")
    private Integer proxyPort;
    @UriParam
    private String region;
    
    public AmazonEC2Client getAmazonEc2Client() {
        return amazonEc2Client;
    }

    /**
     * To use a existing configured AmazonEC2Client as client
     */
    public void setAmazonEc2Client(AmazonEC2Client amazonEc2Client) {
        this.amazonEc2Client = amazonEc2Client;
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
    
    public String getAmazonEc2Endpoint() {
        return amazonEc2Endpoint;
    }

    /**
     * The region with which the AWS-EC2 client wants to work with.
     */
    public void setAmazonEc2Endpoint(String amazonEc2Endpoint) {
        this.amazonEc2Endpoint = amazonEc2Endpoint;
    }

    public EC2Operations getOperation() {
        return operation;
    }

    /**
     * The operation to perform. It can be createAndRunInstances, startInstances, stopInstances, terminateInstances, 
     * describeInstances, describeInstancesStatus, rebootInstances, monitorInstances, unmonitorInstances,  
     * createTags or deleteTags
     */
    public void setOperation(EC2Operations operation) {
        this.operation = operation;
    } 
    
    /**
     * To define a proxy host when instantiating the EC2 client
     */
    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    /**
     * To define a proxy port when instantiating the EC2 client
     */
    public Integer getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }
    
    /**
     * The region in which EC2 client needs to work
     */
    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }
    
    // *************************************************
    //
    // *************************************************

    public EC2Configuration copy() {
        try {
            return (EC2Configuration)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
