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
package org.apache.camel.component.aws.cw;

import java.util.Date;

import com.amazonaws.Protocol;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class CwConfiguration implements Cloneable {

    @UriPath @Metadata(required = true)
    private String namespace;
    @UriParam
    private AmazonCloudWatch amazonCwClient;
    @UriParam(label = "security", secret = true)
    private String accessKey;
    @UriParam(label = "security", secret = true)
    private String secretKey;
    @UriParam
    private String name;
    @UriParam
    private Double value;
    @UriParam
    private String unit;
    @UriParam
    private Date timestamp;
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

    public String getName() {
        return name;
    }

    /**
     * The metric name
     */
    public void setName(String name) {
        this.name = name;
    }

    public Double getValue() {
        return value;
    }

    /**
     * The metric value
     */
    public void setValue(Double value) {
        this.value = value;
    }

    public String getUnit() {
        return unit;
    }

    /**
     * The metric unit
     */
    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getNamespace() {
        return namespace;
    }

    /**
     * The metric namespace
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * The metric timestamp
     */
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public AmazonCloudWatch getAmazonCwClient() {
        return amazonCwClient;
    }

    /**
     * To use the AmazonCloudWatch as the client
     */
    public void setAmazonCwClient(AmazonCloudWatch amazonCwClient) {
        this.amazonCwClient = amazonCwClient;
    }
    
    public Protocol getProxyProtocol() {
        return proxyProtocol;
    }

    /**
     * To define a proxy protocol when instantiating the CW client
     */
    public void setProxyProtocol(Protocol proxyProtocol) {
        this.proxyProtocol = proxyProtocol;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * To define a proxy host when instantiating the CW client
     */
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    /**
     * To define a proxy port when instantiating the CW client
     */
    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getRegion() {
        return region;
    }

    /**
     * The region in which CW client needs to work. When using this parameter, the configuration will expect the capitalized name of the region (for example AP_EAST_1)
     * You'll need to use the name Regions.EU_WEST_1.name()
     */
    public void setRegion(String region) {
        this.region = region;
    }

    // *************************************************
    //
    // *************************************************

    public CwConfiguration copy() {
        try {
            return (CwConfiguration)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
