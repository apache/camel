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

import org.apache.camel.spi.Metadata;

@Metadata(label = "routing,cloud,service-discovery")
@XmlRootElement(name = "zookeeperServiceDiscovery")
@XmlAccessorType(XmlAccessType.FIELD)
public class ZooKeeperServiceCallServiceDiscoveryConfiguration extends ServiceCallServiceDiscoveryConfiguration {
    @XmlAttribute(required = true)
    private String nodes;
    @XmlAttribute
    private String namespace;
    @XmlAttribute
    private String reconnectBaseSleepTime;
    @XmlAttribute
    private String reconnectMaxSleepTime;
    @XmlAttribute
    private Integer reconnectMaxRetries;
    @XmlAttribute
    private String sessionTimeout;
    @XmlAttribute
    private String connectionTimeout;
    @XmlAttribute(required = true)
    private String basePath;


    public ZooKeeperServiceCallServiceDiscoveryConfiguration() {
        this(null);
    }

    public ZooKeeperServiceCallServiceDiscoveryConfiguration(ServiceCallDefinition parent) {
        super(parent, "zookeeper-service-discovery");
    }

    // *************************************************************************
    // Getter/Setter
    // *************************************************************************

    public String getNodes() {
        return nodes;
    }

    /**
     * A comma separate list of servers to connect to in the form host:port
     */
    public void setNodes(String nodes) {
        this.nodes = nodes;
    }

    public String getNamespace() {
        return namespace;
    }

    /**
     * As ZooKeeper is a shared space, users of a given cluster should stay within
     * a pre-defined namespace. If a namespace is set here, all paths will get pre-pended
     * with the namespace
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getReconnectBaseSleepTime() {
        return reconnectBaseSleepTime;
    }

    /**
     * Initial amount of time to wait between retries.
     */
    public void setReconnectBaseSleepTime(String reconnectBaseSleepTime) {
        this.reconnectBaseSleepTime = reconnectBaseSleepTime;
    }

    public String getReconnectMaxSleepTime() {
        return reconnectMaxSleepTime;
    }

    /**
     * Max time in ms to sleep on each retry
     */
    public void setReconnectMaxSleepTime(String reconnectMaxSleepTime) {
        this.reconnectMaxSleepTime = reconnectMaxSleepTime;
    }

    public Integer getReconnectMaxRetries() {
        return reconnectMaxRetries;
    }

    /**
     * Max number of times to retry
     */
    public void setReconnectMaxRetries(Integer reconnectMaxRetries) {
        this.reconnectMaxRetries = reconnectMaxRetries;
    }

    public String getSessionTimeout() {
        return sessionTimeout;
    }

    /**
     * Session timeout.
     */
    public void setSessionTimeout(String sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }

    public String getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * Connection timeout.
     */
    public void setConnectionTimeout(String connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public String getBasePath() {
        return basePath;
    }

    /**
     * Set the base path to store in ZK
     */
    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    // *************************************************************************
    // Fluent API
    // *************************************************************************

    public ZooKeeperServiceCallServiceDiscoveryConfiguration nodes(String nodes) {
        setNodes(nodes);
        return this;
    }

    public ZooKeeperServiceCallServiceDiscoveryConfiguration namespace(String namespace) {
        setNamespace(namespace);
        return this;
    }

    public ZooKeeperServiceCallServiceDiscoveryConfiguration reconnectBaseSleepTime(String reconnectBaseSleepTime) {
        setReconnectBaseSleepTime(reconnectBaseSleepTime);
        return this;
    }

    public ZooKeeperServiceCallServiceDiscoveryConfiguration reconnectMaxSleepTime(String reconnectMaxSleepTime) {
        setReconnectMaxSleepTime(reconnectMaxSleepTime);
        return this;
    }

    public ZooKeeperServiceCallServiceDiscoveryConfiguration reconnectMaxRetries(int reconnectMaxRetries) {
        setReconnectMaxRetries(reconnectMaxRetries);
        return this;
    }

    public ZooKeeperServiceCallServiceDiscoveryConfiguration sessionTimeout(String sessionTimeout) {
        setSessionTimeout(sessionTimeout);
        return this;
    }

    public ZooKeeperServiceCallServiceDiscoveryConfiguration connectionTimeout(String connectionTimeout) {
        setConnectionTimeout(connectionTimeout);
        return this;
    }

    public ZooKeeperServiceCallServiceDiscoveryConfiguration basePath(String basePath) {
        setBasePath(basePath);
        return this;
    }
}