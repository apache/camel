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

@Metadata(label = "routing,cloud,load-balancing")
@XmlRootElement(name = "ribbonLoadBalancer")
@XmlAccessorType(XmlAccessType.FIELD)
public class RibbonServiceCallLoadBalancerConfiguration extends ServiceCallLoadBalancerConfiguration {
    @XmlAttribute
    private String clientName;

    public RibbonServiceCallLoadBalancerConfiguration() {
        this(null);
    }

    public RibbonServiceCallLoadBalancerConfiguration(ServiceCallDefinition parent) {
        super(parent, "ribbon-load-balancer");
    }

    // *************************************************************************
    // Properties
    // *************************************************************************

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
     * Sets the Ribbon client name
     */
    public RibbonServiceCallLoadBalancerConfiguration clientName(String clientName) {
        setClientName(clientName);
        return this;
    }
}
