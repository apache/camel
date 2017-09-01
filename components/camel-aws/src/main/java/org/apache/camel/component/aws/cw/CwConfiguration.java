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
package org.apache.camel.component.aws.cw;

import java.util.Date;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class CwConfiguration implements Cloneable {

    @UriPath @Metadata(required = "true")
    private String namespace;
    @UriParam
    private AmazonCloudWatch amazonCwClient;
    @UriParam
    private String amazonCwEndpoint;
    @UriParam
    private String accessKey;
    @UriParam
    private String secretKey;
    @UriParam
    private String name;
    @UriParam
    private Double value;
    @UriParam
    private String unit;
    @UriParam
    private Date timestamp;
    @UriParam
    private String proxyHost;
    @UriParam
    private Integer proxyPort;
    @UriParam
    private String region;
    

    /**
     * The endpoint with which the AWS-CW client wants to work with.
     */
    public void setAmazonCwEndpoint(String amazonCwEndpoint) {
        this.amazonCwEndpoint = amazonCwEndpoint;
    }

    public String getAmazonCwEndpoint() {
        return amazonCwEndpoint;
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
    
    
    /**
     * To define a proxy host when instantiating the CW client
     */
    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    /**
     * To define a proxy port when instantiating the CW client
     */
    public Integer getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    /**
     * The region in which CW client needs to work
     */
    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

}