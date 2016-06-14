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

package org.apache.camel.model.remote;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Metadata;
import org.apache.camel.util.jsse.SSLContextParameters;

/**
 * Consul remote service call configuration
 */
@Metadata(label = "eip,routing,remote")
@XmlRootElement(name = "consulConfiguration")
@XmlAccessorType(XmlAccessType.FIELD)
public class ConsulConfigurationDefinition extends ServiceCallConfigurationDefinition {

    @XmlAttribute
    private String url;
    @XmlAttribute
    private String dc;
    @XmlAttribute @Metadata(label = "security")
    private String aclToken;
    @XmlAttribute @Metadata(label = "security")
    private String userName;
    @XmlAttribute @Metadata(label = "security")
    private String password;
    @XmlAttribute
    private Long connectTimeoutMillis;
    @XmlAttribute
    private Long readTimeoutMillis;
    @XmlAttribute
    private Long writeTimeoutMillis;
    @XmlAttribute @Metadata(defaultValue = "10")
    private Integer blockSeconds = 10;
    @XmlTransient
    private SSLContextParameters sslContextParameters;

    public ConsulConfigurationDefinition() {
    }

    public ConsulConfigurationDefinition(ServiceCallDefinition parent) {
        super(parent);
    }

    // -------------------------------------------------------------------------
    // Getter/Setter
    // -------------------------------------------------------------------------

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDc() {
        return dc;
    }

    public void setDc(String dc) {
        this.dc = dc;
    }

    public String getAclToken() {
        return aclToken;
    }

    public void setAclToken(String aclToken) {
        this.aclToken = aclToken;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Long getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(Long connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public Long getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public void setReadTimeoutMillis(Long readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public Long getWriteTimeoutMillis() {
        return writeTimeoutMillis;
    }

    public void setWriteTimeoutMillis(Long writeTimeoutMillis) {
        this.writeTimeoutMillis = writeTimeoutMillis;
    }

    public Integer getBlockSeconds() {
        return blockSeconds;
    }

    public void setBlockSeconds(Integer blockSeconds) {
        this.blockSeconds = blockSeconds;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    // -------------------------------------------------------------------------
    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * The Consul agent URL
     */
    public ConsulConfigurationDefinition url(String url) {
        setUrl(url);
        return this;
    }

    /**
     * The data center
     */
    public ConsulConfigurationDefinition dc(String dc) {
        setDc(dc);
        return this;
    }

    /**
     * Sets the ACL token to be used with Consul
     */
    public ConsulConfigurationDefinition aclToken(String aclToken) {
        setAclToken(aclToken);
        return this;
    }

    /**
     * Sets the username to be used for basic authentication
     */
    public ConsulConfigurationDefinition userName(String userName) {
        setUserName(userName);
        return this;
    }

    /**
     * Sets the password to be used for basic authentication
     */
    public ConsulConfigurationDefinition password(String password) {
        setPassword(password);
        return this;
    }

    /**
     * Connect timeout for OkHttpClient
     */
    public ConsulConfigurationDefinition connectTimeoutMillis(Long connectTimeoutMillis) {
        setConnectTimeoutMillis(connectTimeoutMillis);
        return this;
    }

    /**
     * Read timeout for OkHttpClient
     */
    public ConsulConfigurationDefinition readTimeoutMillis(Long readTimeoutMillis) {
        setReadTimeoutMillis(readTimeoutMillis);
        return this;
    }

    /**
     * Write timeout for OkHttpClient
     */
    public ConsulConfigurationDefinition writeTimeoutMillis(Long writeTimeoutMillis) {
        setWriteTimeoutMillis(writeTimeoutMillis);
        return this;
    }

    /**
     * The second to wait for a watch event, default 10 seconds
     */
    public ConsulConfigurationDefinition blockSeconds(Integer blockSeconds) {
        setBlockSeconds(blockSeconds);
        return this;
    }

    /**
     * To configure security using SSLContextParameters.
     */
    public ConsulConfigurationDefinition sslContextParameters(SSLContextParameters sslContextParameters) {
        setSslContextParameters(sslContextParameters);
        return this;
    }
}
