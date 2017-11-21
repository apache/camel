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
package org.apache.camel.component.aws.lambda;

import com.amazonaws.services.lambda.AWSLambda;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class LambdaConfiguration {

    @UriPath
    @Metadata(required = "true")
    private String function;
    @UriParam
    @Metadata(required = "true")
    private LambdaOperations operation;
    @UriParam
    private String awsLambdaEndpoint;
    @UriParam(label = "security", secret = true)
    private String accessKey;
    @UriParam(label = "security", secret = true)
    private String secretKey;
    @UriParam(label = "producer")
    private String region;
    @UriParam(label = "proxy")
    private String proxyHost;
    @UriParam(label = "proxy")
    private Integer proxyPort;
    @UriParam(label = "advanced")
    private AWSLambda awsLambdaClient;

    public String getFunction() {
        return function;
    }

    /**
     * Name of the Lambda function.
     */
    public void setFunction(String function) {
        this.function = function;
    }

    public AWSLambda getAwsLambdaClient() {
        return awsLambdaClient;
    }

    /**
     * To use a existing configured AwsLambdaClient as client
     */
    public void setAwsLambdaClient(AWSLambda awsLambdaClient) {
        this.awsLambdaClient = awsLambdaClient;
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

    public String getRegion() {
        return region;
    }

    /**
     * Amazon AWS Region
     */
    public void setRegion(String region) {
        this.region = region;
    }

    public String getAwsLambdaEndpoint() {
        return awsLambdaEndpoint;
    }

    /**
     * The region with which the AWS-Lambda client wants to work with.
     */
    public void setAwsLambdaEndpoint(String awsLambdaEndpoint) {
        this.awsLambdaEndpoint = awsLambdaEndpoint;
    }

    public LambdaOperations getOperation() {
        return operation;
    }

    /**
     * The operation to perform. It can be listFunctions, getFunction, createFunction, deleteFunction or invokeFunction
     */
    public void setOperation(LambdaOperations operation) {
        this.operation = operation;
    }

    /**
     * To define a proxy host when instantiating the Lambda client
     */
    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    /**
     * To define a proxy port when instantiating the Lambda client
     */
    public Integer getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

}