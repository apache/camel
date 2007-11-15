/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.impl.RouteContext;
import org.apache.camel.processor.ChoiceProcessor;
import org.apache.camel.processor.FilterProcessor;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.camel.processor.loadbalancer.RoundRobinLoadBalancer;
import org.apache.camel.processor.loadbalancer.StickyLoadBalancer;
import org.apache.camel.processor.loadbalancer.TopicLoadBalancer;
import org.apache.camel.processor.loadbalancer.RandomLoadBalancer;
import org.apache.camel.util.CollectionStringBuffer;

@XmlRootElement(name = "loadBalance")
@XmlAccessorType(XmlAccessType.NONE)
public class LoadBalanceType extends OutputType<LoadBalanceType> {
   
    // how to define it in XML    
    private LoadBalancer loadBalancer;
    
       
    protected Processor createOutputsProcessor(RouteContext routeContext, Collection<ProcessorType<?>> outputs)
        throws Exception {
        assert loadBalancer != null;
        for (ProcessorType processorType : outputs) {
            //The outputs should be the SendProcessor
            SendProcessor processor =(SendProcessor) processorType.createProcessor(routeContext);
            
            loadBalancer.addProcessor(processor);
        } 
        return loadBalancer;
    }
    
    // when this method will be called
    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        assert loadBalancer != null;        
        for (ProcessorType processorType : getOutputs()) {
            //The outputs should be the SendProcessor
            SendProcessor processor =(SendProcessor) processorType.createProcessor(routeContext);
            
            loadBalancer.addProcessor(processor);
        } 
        
        return loadBalancer;
    }

    // Fluent API
    // -------------------------------------------------------------------------
    public LoadBalanceType setLoadBalancer(LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
        return this;
    }
    
    public LoadBalanceType roundRobin() {
        loadBalancer = new RoundRobinLoadBalancer();
        return this;        
    }
    
    public LoadBalanceType random() {
        loadBalancer = new RandomLoadBalancer();
        return this;
    }
    
    public LoadBalanceType sticky(Expression<Exchange> correlationExpression) {
        loadBalancer = new StickyLoadBalancer(correlationExpression);
        return this;
    }
    
    public LoadBalanceType topic() {
        loadBalancer = new TopicLoadBalancer();
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
        return "LoadBanlance[ " + getOutputs() + "]";
    }

    

    

}
