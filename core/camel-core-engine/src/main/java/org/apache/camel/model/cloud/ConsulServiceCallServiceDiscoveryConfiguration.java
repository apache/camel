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
package org.apache.camel.model.cloud;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Metadata;
import org.apache.camel.support.jsse.SSLContextParameters;

@Metadata(label = "routing,cloud,service-discovery")
@XmlRootElement(name = "consulServiceDiscovery")
@XmlAccessorType(XmlAccessType.FIELD)
public class ConsulServiceCallServiceDiscoveryConfiguration extends ServiceCallServiceDiscoveryConfiguration {
    @XmlAttribute
    private String url;
    @XmlAttribute
    private String datacenter;
    @XmlAttribute
    @Metadata(label = "security")
    private String aclToken;
    @XmlAttribute
    @Metadata(label = "security")
    private String userName;
    @XmlAttribute
    @Metadata(label = "security")
    private String password;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Long")
    private String connectTimeoutMillis;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Long")
    private String readTimeoutMillis;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Long")
    private String writeTimeoutMillis;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer", defaultValue = "10")
    private String blockSeconds = Integer.toString(10);
    @XmlTransient
    private SSLContextParameters sslContextParameters;

    public ConsulServiceCallServiceDiscoveryConfiguration() {
        this(null);
    }

    public ConsulServiceCallServiceDiscoveryConfiguration(ServiceCallDefinition parent) {
        super(parent, "consul-service-discovery");
    }

    // *************************************************************************
    // Getter/Setter
    // *************************************************************************

    /**
     * The Consul agent URL
     */
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDatacenter() {
        return datacenter;
    }

    /**
     * The data center
     */
    public void setDatacenter(String datacenter) {
        this.datacenter = datacenter;
    }

    public String getAclToken() {
        return aclToken;
    }

    /**
     * Sets the ACL token to be used with Consul
     */
    public void setAclToken(String aclToken) {
        this.aclToken = aclToken;
    }

    public String getUserName() {
        return userName;
    }

    /**
     * Sets the username to be used for basic authentication
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Sets the password to be used for basic authentication
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    /**
     * Connect timeout for OkHttpClient
     */
    public void setConnectTimeoutMillis(String connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public String getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    /**
     * Read timeout for OkHttpClient
     */
    public void setReadTimeoutMillis(String readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public String getWriteTimeoutMillis() {
        return writeTimeoutMillis;
    }

    /**
     * Write timeout for OkHttpClient
     */
    public void setWriteTimeoutMillis(String writeTimeoutMillis) {
        this.writeTimeoutMillis = writeTimeoutMillis;
    }

    public String getBlockSeconds() {
        return blockSeconds;
    }

    /**
     * The seconds to wait for a watch event, default 10 seconds
     */
    public void setBlockSeconds(String blockSeconds) {
        this.blockSeconds = blockSeconds;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * To configure security using SSLContextParameters.
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    // *************************************************************************
    // Fluent API
    // *************************************************************************

    /**
     * The Consul agent URL
     */
    public ConsulServiceCallServiceDiscoveryConfiguration url(String url) {
        setUrl(url);
        return this;
    }

    /**
     * The data center
     */
    public ConsulServiceCallServiceDiscoveryConfiguration dataCenter(String dc) {
        setDatacenter(dc);
        return this;
    }

    /**
     * Sets the ACL token to be used with Consul
     */
    public ConsulServiceCallServiceDiscoveryConfiguration aclToken(String aclToken) {
        setAclToken(aclToken);
        return this;
    }

    /**
     * Sets the username to be used for basic authentication
     */
    public ConsulServiceCallServiceDiscoveryConfiguration userName(String userName) {
        setUserName(userName);
        return this;
    }

    /**
     * Sets the password to be used for basic authentication
     */
    public ConsulServiceCallServiceDiscoveryConfiguration password(String password) {
        setPassword(password);
        return this;
    }

    /**
     * Connect timeout for OkHttpClient
     */
    public ConsulServiceCallServiceDiscoveryConfiguration connectTimeoutMillis(long connectTimeoutMillis) {
        return connectTimeoutMillis(Long.toString(connectTimeoutMillis));
    }

    /**
     * Connect timeout for OkHttpClient
     */
    public ConsulServiceCallServiceDiscoveryConfiguration connectTimeoutMillis(String connectTimeoutMillis) {
        setConnectTimeoutMillis(connectTimeoutMillis);
        return this;
    }

    /**
     * Read timeout for OkHttpClient
     */
    public ConsulServiceCallServiceDiscoveryConfiguration readTimeoutMillis(Long readTimeoutMillis) {
        return readTimeoutMillis(Long.toString(readTimeoutMillis));
    }

    /**
     * Read timeout for OkHttpClient
     */
    public ConsulServiceCallServiceDiscoveryConfiguration readTimeoutMillis(String readTimeoutMillis) {
        setReadTimeoutMillis(readTimeoutMillis);
        return this;
    }

    /**
     * Write timeout for OkHttpClient
     */
    public ConsulServiceCallServiceDiscoveryConfiguration writeTimeoutMillis(Long writeTimeoutMillis) {
        return writeTimeoutMillis(Long.toString(writeTimeoutMillis));
    }

    /**
     * Write timeout for OkHttpClient
     */
    public ConsulServiceCallServiceDiscoveryConfiguration writeTimeoutMillis(String writeTimeoutMillis) {
        setWriteTimeoutMillis(writeTimeoutMillis);
        return this;
    }

    /**
     * The seconds to wait for a watch event, default 10 seconds
     */
    public ConsulServiceCallServiceDiscoveryConfiguration blockSeconds(Integer blockSeconds) {
        return blockSeconds(Integer.toString(blockSeconds));
    }

    /**
     * The seconds to wait for a watch event, default 10 seconds
     */
    public ConsulServiceCallServiceDiscoveryConfiguration blockSeconds(String blockSeconds) {
        setBlockSeconds(blockSeconds);
        return this;
    }

    /**
     * To configure security using SSLContextParameters.
     */
    public ConsulServiceCallServiceDiscoveryConfiguration sslContextParameters(SSLContextParameters sslContextParameters) {
        setSslContextParameters(sslContextParameters);
        return this;
    }
}
