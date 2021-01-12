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

import org.apache.camel.spi.Metadata;

@Metadata(label = "routing,cloud,load-balancing")
@XmlRootElement(name = "ribbonLoadBalancer")
@XmlAccessorType(XmlAccessType.FIELD)
public class RibbonServiceCallServiceLoadBalancerConfiguration extends ServiceCallServiceLoadBalancerConfiguration {
    @XmlAttribute
    private String namespace;
    @XmlAttribute
    private String username;
    @XmlAttribute
    private String password;
    @XmlAttribute
    private String clientName;

    public RibbonServiceCallServiceLoadBalancerConfiguration() {
        this(null);
    }

    public RibbonServiceCallServiceLoadBalancerConfiguration(ServiceCallDefinition parent) {
        super(parent, "ribbon-service-load-balancer");
    }

    // *************************************************************************
    // Properties
    // *************************************************************************

    public String getNamespace() {
        return namespace;
    }

    /**
     * The namespace
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getUsername() {
        return username;
    }

    /**
     * The username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * The password
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getClientName() {
        return clientName;
    }

    /**
     * Sets the Ribbon client name
     */
    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    // *************************************************************************
    // Fluent API
    // *************************************************************************

    /**
     * Sets the namespace
     */
    public RibbonServiceCallServiceLoadBalancerConfiguration namespace(String namespace) {
        setNamespace(namespace);
        return this;
    }

    /**
     * Sets the username
     */
    public RibbonServiceCallServiceLoadBalancerConfiguration username(String username) {
        setUsername(username);
        return this;
    }

    /**
     * Sets the password
     */
    public RibbonServiceCallServiceLoadBalancerConfiguration password(String password) {
        setPassword(password);
        return this;
    }

    /**
     * Sets the Ribbon client name
     */
    public RibbonServiceCallServiceLoadBalancerConfiguration clientName(String clientName) {
        setClientName(clientName);
        return this;
    }
}
