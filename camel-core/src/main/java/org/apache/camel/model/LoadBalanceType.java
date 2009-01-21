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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.model.loadbalancer.LoadBalancerType;
import org.apache.camel.model.loadbalancer.RandomLoadBalanceStrategy;
import org.apache.camel.model.loadbalancer.RoundRobinLoadBalanceStrategy;
import org.apache.camel.model.loadbalancer.StickyLoadBalanceStrategy;
import org.apache.camel.model.loadbalancer.TopicLoadBalanceStrategy;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.camel.processor.loadbalancer.RandomLoadBalancer;
import org.apache.camel.processor.loadbalancer.RoundRobinLoadBalancer;
import org.apache.camel.processor.loadbalancer.StickyLoadBalancer;
import org.apache.camel.processor.loadbalancer.TopicLoadBalancer;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.CollectionStringBuffer;

/**
 * Represents an XML &lt;loadBalance/&gt; element
 */
@XmlRootElement(name = "loadBalance")
@XmlAccessorType(XmlAccessType.FIELD)
public class LoadBalanceType extends ProcessorType<LoadBalanceType> {
    @XmlAttribute(required = false)
    private String ref;

    @XmlElements({
        @XmlElement(required = false, name = "roundRobin", type = RoundRobinLoadBalanceStrategy.class),
        @XmlElement(required = false, name = "random", type = RandomLoadBalanceStrategy.class),
        @XmlElement(required = false, name = "sticky", type = StickyLoadBalanceStrategy.class),
        @XmlElement(required = false, name = "topic", type = TopicLoadBalanceStrategy.class)}
        )
    private LoadBalancerType loadBalancerType;

    @XmlElementRef
    private List<ProcessorType<?>> outputs = new ArrayList<ProcessorType<?>>();

    public LoadBalanceType() {
    }

    @Override
    public String getShortName() {
        return "loadbalance";
    }

    public List<ProcessorType<?>> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<ProcessorType<?>> outputs) {
        this.outputs = outputs;
        if (outputs != null) {
            for (ProcessorType output : outputs) {
                configureChild(output);
            }
        }
    }


    @Override
    protected void configureChild(ProcessorType output) {
        super.configureChild(output);
        if (isInheritErrorHandler()) {
            output.setErrorHandlerBuilder(getErrorHandlerBuilder());
        }
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public LoadBalancerType getLoadBalancerType() {
        return loadBalancerType;
    }

    public void setLoadBalancerType(LoadBalancerType loadbalancer) {
        loadBalancerType = loadbalancer;
    }

    protected Processor createOutputsProcessor(RouteContext routeContext, Collection<ProcessorType<?>> outputs)
        throws Exception {
        LoadBalancer loadBalancer = LoadBalancerType.getLoadBalancer(routeContext, loadBalancerType, ref);
        for (ProcessorType processorType : outputs) {
            // The outputs should be the SendProcessor
            SendProcessor processor = (SendProcessor) processorType.createProcessor(routeContext);
            loadBalancer.addProcessor(processor);
        }
        return loadBalancer;
    }
    
    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        LoadBalancer loadBalancer = LoadBalancerType.getLoadBalancer(routeContext, loadBalancerType, ref);
        for (ProcessorType processorType : getOutputs()) {            
            Processor processor = processorType.createProcessor(routeContext);
            processor = processorType.wrapProcessorInInterceptors(routeContext, processor);
            loadBalancer.addProcessor(processor);
        }

        return loadBalancer;
    }
    
    // Fluent API
    // -------------------------------------------------------------------------
    public LoadBalanceType setLoadBalancer(LoadBalancer loadBalancer) {
        loadBalancerType = new LoadBalancerType(loadBalancer);
        return this;
    }

    public LoadBalanceType roundRobin() {
        loadBalancerType = new LoadBalancerType(new RoundRobinLoadBalancer());
        return this;
    }

    public LoadBalanceType random() {
        loadBalancerType = new LoadBalancerType(new RandomLoadBalancer());
        return this;
    }

    public LoadBalanceType sticky(Expression<Exchange> correlationExpression) {
        loadBalancerType = new LoadBalancerType(new StickyLoadBalancer(correlationExpression));
        return this;
    }

    /**
     * @deprecated will be removed in Camel 2.0, use multicast instead
     */
    public LoadBalanceType topic() {
        loadBalancerType = new LoadBalancerType(new TopicLoadBalancer());
        return this;
    }

    @Override
    public String getLabel() {
        CollectionStringBuffer buffer = new CollectionStringBuffer();
        List<ProcessorType<?>> list = getOutputs();
        for (ProcessorType<?> processorType : list) {
            buffer.append(processorType.getLabel());
        }
        return buffer.toString();
    }

    @Override
    public String toString() {
        if (loadBalancerType != null) {
            return "LoadBalanceType[" + loadBalancerType + ", " + getOutputs() + "]";
        } else {
            return "LoadBalanceType[ref: " + ref + ", " + getOutputs() + "]";
        }
    }

}
