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
package org.apache.camel.component.aws.lambda;

import com.amazonaws.Protocol;
import com.amazonaws.services.lambda.AWSLambda;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class LambdaConfiguration implements Cloneable {

    @UriParam(defaultValue = "invokeFunction")
    private LambdaOperations operation = LambdaOperations.invokeFunction;
    @UriParam(label = "security", secret = true)
    private String accessKey;
    @UriParam(label = "security", secret = true)
    private String secretKey;
    @UriParam(label = "producer")
    private String region;
    @UriParam(label = "proxy", enums = "HTTP,HTTPS", defaultValue = "HTTPS")
    private Protocol proxyProtocol = Protocol.HTTPS;
    @UriParam(label = "proxy")
    private String proxyHost;
    @UriParam(label = "proxy")
    private Integer proxyPort;
    @UriParam(label = "advanced")
    private AWSLambda awsLambdaClient;
    @UriParam(label = "common", defaultValue = "true")
    private boolean autoDiscoverClient = true;

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
     * Amazon AWS Region. When using this parameter, the configuration will expect the capitalized name of the region
     * (for example AP_EAST_1) You'll need to use the name Regions.EU_WEST_1.name()
     */
    public void setRegion(String region) {
        this.region = region;
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

    public Protocol getProxyProtocol() {
        return proxyProtocol;
    }

    /**
     * To define a proxy protocol when instantiating the Lambda client
     */
    public void setProxyProtocol(Protocol proxyProtocol) {
        this.proxyProtocol = proxyProtocol;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * To define a proxy host when instantiating the Lambda client
     */
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    /**
     * To define a proxy port when instantiating the Lambda client
     */
    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public boolean isAutoDiscoverClient() {
        return autoDiscoverClient;
    }

    /**
     * Setting the autoDiscoverClient mechanism, if true, the component will look for a client instance in the registry
     * automatically otherwise it will skip that checking.
     */
    public void setAutoDiscoverClient(boolean autoDiscoverClient) {
        this.autoDiscoverClient = autoDiscoverClient;
    }

    // *************************************************
    //
    // *************************************************

    public LambdaConfiguration copy() {
        try {
            return (LambdaConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

}
