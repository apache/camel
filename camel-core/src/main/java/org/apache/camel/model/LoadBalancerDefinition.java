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
package org.apache.camel.model;

import java.util.Map;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyAttribute;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.namespace.QName;

import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;

/**
 * Balances message processing among a number of nodes
 */
@Metadata(label = "eip,routing")
@XmlType(name = "loadBalancer")
@XmlAccessorType(XmlAccessType.FIELD)
public class LoadBalancerDefinition extends IdentifiedType implements OtherAttributesAware {
    @XmlTransient
    private LoadBalancer loadBalancer;
    @XmlTransient
    private String loadBalancerTypeName;
    // use xs:any to support optional property placeholders
    @XmlAnyAttribute
    private Map<QName, Object> otherAttributes;

    public LoadBalancerDefinition() {
    }

    public LoadBalancerDefinition(LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
    }

    protected LoadBalancerDefinition(String loadBalancerTypeName) {
        this.loadBalancerTypeName = loadBalancerTypeName;
    }

    /**
     * Sets a named property on the data format instance using introspection
     */
    protected void setProperty(Object bean, String name, Object value) {
        try {
            IntrospectionSupport.setProperty(bean, name, value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to set property " + name + " on " + bean + ". Reason: " + e, e);
        }
    }

    /**
     * Maximum number of outputs, as some load balancers only support 1 processor
     */
    protected int getMaximumNumberOfOutputs() {
        return Integer.MAX_VALUE;
    }

    /**
     * Allows derived classes to customize the load balancer
     */
    protected void configureLoadBalancer(LoadBalancer loadBalancer) {
    }

    public LoadBalancer getLoadBalancer(RouteContext routeContext) {
        return loadBalancer;
    }

    public void setLoadBalancer(LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
    }

    @Override
    public Map<QName, Object> getOtherAttributes() {
        return otherAttributes;
    }

    @Override
    public void setOtherAttributes(Map<QName, Object> otherAttributes) {
        this.otherAttributes = otherAttributes;
    }

    /**
     * Factory method to create the load balancer from the loadBalancerTypeName
     */
    protected LoadBalancer createLoadBalancer(RouteContext routeContext) {
        ObjectHelper.notEmpty(loadBalancerTypeName, "loadBalancerTypeName", this);

        LoadBalancer answer = null;
        if (loadBalancerTypeName != null) {
            Class<?> type = routeContext.getCamelContext().getClassResolver().resolveClass(loadBalancerTypeName, LoadBalancer.class);
            if (type == null) {
                throw new IllegalArgumentException("Cannot find class: " + loadBalancerTypeName + " in the classpath");
            }
            answer = (LoadBalancer) routeContext.getCamelContext().getInjector().newInstance(type);
            configureLoadBalancer(answer);
        }

        return answer;
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
