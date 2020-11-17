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
package org.apache.camel.component.aws2.eventbridge;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;

@UriParams
public class EventbridgeConfiguration implements Cloneable {

    private String eventbusName = "default";
    @UriParam
    @Metadata(autowired = true)
    private EventBridgeClient eventbridgeClient;
    @UriParam(label = "security", secret = true)
    private String accessKey;
    @UriParam(label = "security", secret = true)
    private String secretKey;
    @UriParam
    @Metadata(required = true, defaultValue = "putRule")
    private EventbridgeOperations operation = EventbridgeOperations.putRule;
    @UriParam(enums = "HTTP,HTTPS", defaultValue = "HTTPS")
    private Protocol proxyProtocol = Protocol.HTTPS;
    @UriParam
    private String proxyHost;
    @UriParam
    private Integer proxyPort;
    @UriParam
    private String region;
    @UriParam(defaultValue = "false")
    private boolean pojoRequest;
    @UriParam(defaultValue = "false")
    private boolean trustAllCertificates;
    @UriParam
    private String eventPatternFile;

    public EventBridgeClient getEventbridgeClient() {
        return eventbridgeClient;
    }

    /**
     * To use a existing configured AWS Eventbridge as client
     */
    public void setEventbridgeClient(EventBridgeClient eventbridgeClient) {
        this.eventbridgeClient = eventbridgeClient;
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

    public EventbridgeOperations getOperation() {
        return operation;
    }

    /**
     * The operation to perform
     */
    public void setOperation(EventbridgeOperations operation) {
        this.operation = operation;
    }

    public Protocol getProxyProtocol() {
        return proxyProtocol;
    }

    /**
     * To define a proxy protocol when instantiating the Eventbridge client
     */
    public void setProxyProtocol(Protocol proxyProtocol) {
        this.proxyProtocol = proxyProtocol;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * To define a proxy host when instantiating the Eventbridge client
     */
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    /**
     * To define a proxy port when instantiating the Eventbridge client
     */
    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getRegion() {
        return region;
    }

    /**
     * The region in which Eventbridge client needs to work. When using this parameter, the configuration will expect
     * the lowercase name of the region (for example ap-east-1) You'll need to use the name Region.EU_WEST_1.id()
     */
    public void setRegion(String region) {
        this.region = region;
    }

    public boolean isPojoRequest() {
        return pojoRequest;
    }

    /**
     * If we want to use a POJO request as body or not
     */
    public void setPojoRequest(boolean pojoRequest) {
        this.pojoRequest = pojoRequest;
    }

    public boolean isTrustAllCertificates() {
        return trustAllCertificates;
    }

    /**
     * If we want to trust all certificates in case of overriding the endpoint
     */
    public void setTrustAllCertificates(boolean trustAllCertificates) {
        this.trustAllCertificates = trustAllCertificates;
    }

    public String getEventPatternFile() {
        return eventPatternFile;
    }

    /**
     * EventPattern File
     */
    public void setEventPatternFile(String eventPatternFile) {
        this.eventPatternFile = eventPatternFile;
    }

    public String getEventbusName() {
        return eventbusName;
    }

    /**
     * The eventbus name, the default value is default and this means it will be the AWS event bus of your account.
     */
    public void setEventbusName(String eventbusName) {
        this.eventbusName = eventbusName;
    }
    // *************************************************
    //
    // *************************************************

    public EventbridgeConfiguration copy() {
        try {
            return (EventbridgeConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
