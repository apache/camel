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
package org.apache.camel.model.loadbalancer;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.model.IdentifiedType;
import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * Represents an XML &lt;loadBalance/&gt; element
 */
@XmlType(name = "loadBalancerType")
@XmlAccessorType(XmlAccessType.FIELD)
public class LoadBalancerType extends IdentifiedType implements LoadBalancer {

    @XmlTransient
    private LoadBalancer loadBalancer;
    @XmlTransient
    private String loadBalancerTypeName;

    public LoadBalancerType() {
    }

    public LoadBalancerType(LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
    }

    protected LoadBalancerType(String loadBalancerTypeName) {
        this.loadBalancerTypeName = loadBalancerTypeName;
    }

    public static LoadBalancer getLoadBalancer(RouteContext routeContext, LoadBalancerType type, String ref) {
        if (type == null) {
            notNull(ref, "ref or LoadBalancerType");
            LoadBalancer loadBalancer = routeContext.lookup(ref, LoadBalancer.class);
            if (loadBalancer instanceof LoadBalancerType) {
                type = (LoadBalancerType) loadBalancer;
            } else {
                return loadBalancer;
            }
        }
        return type.getLoadBalancer(routeContext);
    }


    /**
     * Sets a named property on the data format instance using introspection
     */
    protected void setProperty(Object bean, String name, Object value) {
        try {
            IntrospectionSupport.setProperty(bean, name, value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to set property " + name + " on " + bean
                                               + ". Reason: " + e, e);
        }
    }

    /**
     * Allows derived classes to customize the load balancer
     */
    protected void configureLoadBalancer(LoadBalancer loadBalancer) {
    }

    public LoadBalancer getLoadBalancer(RouteContext routeContext) {
        if (loadBalancer == null) {
            loadBalancer = createLoadBalancer(routeContext);
            ObjectHelper.notNull(loadBalancer, "loadBalancer");
            configureLoadBalancer(loadBalancer);
        }
        return loadBalancer;
    }

    /**
     * Factory method to create the load balancer instance
     */
    protected LoadBalancer createLoadBalancer(RouteContext routeContext) {
        if (loadBalancerTypeName != null) {
            Class type = ObjectHelper.loadClass(loadBalancerTypeName, getClass().getClassLoader());
            if (type == null) {
                throw new IllegalArgumentException("The class " + loadBalancerTypeName + " is not on the classpath! Cannot use the loadBalancer " + this);
            }
            return (LoadBalancer) ObjectHelper.newInstance(type);
        }
        return null;
    }


    public void addProcessor(Processor processor) {
        ObjectHelper.notNull(loadBalancer, "loadBalancer");
        loadBalancer.addProcessor(processor);
    }

    public List<Processor> getProcessors() {
        ObjectHelper.notNull(loadBalancer, "loadBalancer");
        return loadBalancer.getProcessors();
    }

    public void removeProcessor(Processor processor) {
        ObjectHelper.notNull(loadBalancer, "loadBalancer");
        loadBalancer.removeProcessor(processor);
    }

    public void process(Exchange exchange) throws Exception {
        ObjectHelper.notNull(loadBalancer, "loadBalancer");
        loadBalancer.process(exchange);
    }

}
