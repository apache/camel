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

import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents an XML &lt;loadBalancer/&gt; element
 */
@XmlType(name = "loadBalancer")
@XmlAccessorType(XmlAccessType.FIELD)
public class LoadBalancerDefinition extends IdentifiedType implements LoadBalancer {

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

    public static LoadBalancer getLoadBalancer(RouteContext routeContext, LoadBalancerDefinition type, String ref) {
        if (type == null) {
            ObjectHelper.notNull(ref, "ref or loadBalancer");
            LoadBalancer loadBalancer = routeContext.lookup(ref, LoadBalancer.class);
            if (loadBalancer instanceof LoadBalancerDefinition) {
                type = (LoadBalancerDefinition) loadBalancer;
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
            throw new IllegalArgumentException("Failed to set property " + name + " on " + bean + ". Reason: " + e, e);
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
    @SuppressWarnings("unchecked")
    protected LoadBalancer createLoadBalancer(RouteContext routeContext) {
        if (loadBalancerTypeName != null) {
            Class type = routeContext.getCamelContext().getClassResolver().resolveClass(loadBalancerTypeName);
            if (type == null) {
                throw new IllegalArgumentException("Cannot find class: " + loadBalancerTypeName + " in the classpath");
            }
            return (LoadBalancer) ObjectHelper.newInstance(type);
        }
        return null;
    }


    public void addProcessor(Processor processor) {
        ObjectHelper.notNull(loadBalancer, "loadBalancer", this);
        loadBalancer.addProcessor(processor);
    }

    public List<Processor> getProcessors() {
        ObjectHelper.notNull(loadBalancer, "loadBalancer", this);
        return loadBalancer.getProcessors();
    }

    public void removeProcessor(Processor processor) {
        ObjectHelper.notNull(loadBalancer, "loadBalancer", this);
        loadBalancer.removeProcessor(processor);
    }

    public void process(Exchange exchange) throws Exception {
        ObjectHelper.notNull(loadBalancer, "loadBalancer", this);
        loadBalancer.process(exchange);
    }

    public boolean process(Exchange exchange, final AsyncCallback callback) {
        ObjectHelper.notNull(loadBalancer, "loadBalancer");
        return AsyncProcessorHelper.process(loadBalancer, exchange, new AsyncCallback() {
            public void done(boolean doneSync) {
                // only handle the async case
                if (doneSync) {
                    return;
                } else {
                    callback.done(false);
                }
            }
        });
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
