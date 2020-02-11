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
package org.apache.camel.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.camel.spi.Metadata;

/**
 * Balances message processing among a number of nodes
 */
@Metadata(label = "eip,routing")
@XmlType(name = "loadBalancer")
@XmlAccessorType(XmlAccessType.FIELD)
@SuppressWarnings("rawtypes")
public class LoadBalancerDefinition extends IdentifiedType {
    @XmlTransient
    private LoadBalancer loadBalancer;
    @XmlTransient
    private String loadBalancerTypeName;

    public LoadBalancerDefinition() {
    }

    public LoadBalancerDefinition(LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
    }

    protected LoadBalancerDefinition(String loadBalancerTypeName) {
        this.loadBalancerTypeName = loadBalancerTypeName;
    }

    /**
     * Maximum number of outputs, as some load balancers only support 1
     * processor
     */
    public int getMaximumNumberOfOutputs() {
        return Integer.MAX_VALUE;
    }

    /**
     * Allows derived classes to customize the load balancer
     */
    public void configureLoadBalancer(LoadBalancer loadBalancer) {
    }

    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    public void setLoadBalancer(LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
    }

    public String getLoadBalancerTypeName() {
        return loadBalancerTypeName;
    }

    @Override
    public String toString() {
        if (loadBalancer != null) {
            return loadBalancer.toString();
        } else {
            return loadBalancerTypeName;
        }
    }
}
