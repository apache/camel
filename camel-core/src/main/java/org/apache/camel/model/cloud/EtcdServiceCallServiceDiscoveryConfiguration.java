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
package org.apache.camel.model.cloud;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Metadata;
import org.apache.camel.util.jsse.SSLContextParameters;

@Metadata(label = "routing,cloud,service-discovery")
@XmlRootElement(name = "etcdServiceDiscovery")
@XmlAccessorType(XmlAccessType.FIELD)
public class EtcdServiceCallServiceDiscoveryConfiguration extends ServiceCallServiceDiscoveryConfiguration {
    @XmlAttribute
    private String uris;
    @XmlAttribute @Metadata(label = "security")
    private String userName;
    @XmlAttribute @Metadata(label = "security")
    private String password;
    @XmlAttribute
    private Long timeout;
    @XmlAttribute @Metadata(defaultValue = "/services/")
    private String servicePath = "/services/";
    @XmlTransient
    private SSLContextParameters sslContextParameters;
    @XmlAttribute @Metadata(defaultValue = "on-demand", enums = "on-demand,watch")
    private String type = "on-demand";

    public EtcdServiceCallServiceDiscoveryConfiguration() {
        this(null);
    }

    public EtcdServiceCallServiceDiscoveryConfiguration(ServiceCallDefinition parent) {
        super(parent, "etcd-service-discovery");
    }

    // *************************************************************************
    // Properties
    // *************************************************************************

    public String getUris() {
        return uris;
    }

    /**
     * The URIs the client can connect to.
     */
    public void setUris(String uris) {
        this.uris = uris;
    }

    public String getUserName() {
        return userName;
    }

    /**
     * The user name to use for basic authentication.
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }
    public String getPassword() {
        return password;
    }

    /**
     * The password to use for basic authentication.
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public Long getTimeout() {
        return timeout;
    }

    /**
     * To set the maximum time an action could take to complete.
     */
    public void setTimeout(Long timeout) {
        this.timeout = timeout;
    }

    public String getServicePath() {
        return servicePath;
    }

    /**
     * The path to look for for service discovery
     */
    public void setServicePath(String servicePath) {
        this.servicePath = servicePath;
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

    public String getType() {
        return type;
    }

    /**
     * To set the discovery type, valid values are on-demand and watch.
     */
    public void setType(String type) {
        this.type = type;
    }

    // *************************************************************************
    // Fluent API
    // *************************************************************************

    /**
     * The URIs the client can connect to.
     */
    public EtcdServiceCallServiceDiscoveryConfiguration uris(String uris) {
        setUris(uris);
        return this;
    }

    /**
     * The user name to use for basic authentication.
     */
    public EtcdServiceCallServiceDiscoveryConfiguration userName(String userName) {
        setUserName(userName);
        return this;
    }

    /**
     * The password to use for basic authentication.
     */
    public EtcdServiceCallServiceDiscoveryConfiguration password(String password) {
        setPassword(password);
        return this;
    }

    /**
     * To set the maximum time an action could take to complete.
     */
    public EtcdServiceCallServiceDiscoveryConfiguration timeout(Long timeout) {
        setTimeout(timeout);
        return this;
    }

    /**
     * The path to look for for service discovery
     */
    public EtcdServiceCallServiceDiscoveryConfiguration servicePath(String servicePath) {
        setServicePath(servicePath);
        return this;
    }

    /**
     * To configure security using SSLContextParameters.
     */
    public EtcdServiceCallServiceDiscoveryConfiguration sslContextParameters(SSLContextParameters sslContextParameters) {
        setSslContextParameters(sslContextParameters);
        return this;
    }

    /**
     * To set the discovery type, valid values are on-demand and watch.
     */
    public EtcdServiceCallServiceDiscoveryConfiguration type(String type) {
        setType(type);
        return this;
    }
}