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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Expression;
import org.apache.camel.model.loadbalancer.CustomLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.FailoverLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.RandomLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.RoundRobinLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.StickyLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.TopicLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.WeightedLoadBalancerDefinition;
import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.camel.spi.Metadata;

/**
 * Balances message processing among a number of nodes
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "loadBalance")
@XmlAccessorType(XmlAccessType.FIELD)
public class LoadBalanceDefinition extends OutputDefinition<LoadBalanceDefinition> {
    @XmlElements({@XmlElement(required = false, name = "failover", type = FailoverLoadBalancerDefinition.class),
                  @XmlElement(required = false, name = "random", type = RandomLoadBalancerDefinition.class),
                  @XmlElement(required = false, name = "customLoadBalancer", type = CustomLoadBalancerDefinition.class),
                  @XmlElement(required = false, name = "roundRobin", type = RoundRobinLoadBalancerDefinition.class),
                  @XmlElement(required = false, name = "sticky", type = StickyLoadBalancerDefinition.class),
                  @XmlElement(required = false, name = "topic", type = TopicLoadBalancerDefinition.class),
                  @XmlElement(required = false, name = "weighted", type = WeightedLoadBalancerDefinition.class)})
    private LoadBalancerDefinition loadBalancerType;

    public LoadBalanceDefinition() {
    }

    @Override
    public List<ProcessorDefinition<?>> getOutputs() {
        return outputs;
    }

    @XmlElementRef
    @Override
    public void setOutputs(List<ProcessorDefinition<?>> outputs) {
        super.setOutputs(outputs);
    }

    public LoadBalancerDefinition getLoadBalancerType() {
        return loadBalancerType;
    }

    /**
     * The load balancer to be used
     */
    public void setLoadBalancerType(LoadBalancerDefinition loadbalancer) {
        if (loadBalancerType != null) {
            throw new IllegalArgumentException("Loadbalancer already configured to: " + loadBalancerType + ". Cannot set it to: " + loadbalancer);
        }
        loadBalancerType = loadbalancer;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Uses a custom load balancer
     *
     * @param loadBalancer the load balancer
     * @return the builder
     */
    @Override
    public LoadBalanceDefinition loadBalance(LoadBalancer loadBalancer) {
        CustomLoadBalancerDefinition def = new CustomLoadBalancerDefinition();
        def.setCustomLoadBalancer(loadBalancer);
        setLoadBalancerType(def);
        return this;
    }

    /**
     * Uses fail over load balancer
     * <p/>
     * Will not round robin and inherit the error handler.
     *
     * @return the builder
     */
    public LoadBalanceDefinition failover() {
        return failover(-1, true, false);
    }

    /**
     * Uses fail over load balancer
     * <p/>
     * Will not round robin and inherit the error handler.
     *
     * @param exceptions exception classes which we want to failover if one of
     *            them was thrown
     * @return the builder
     */
    public LoadBalanceDefinition failover(Class<?>... exceptions) {
        return failover(-1, true, false, exceptions);
    }

    /**
     * Uses fail over load balancer
     *
     * @param maximumFailoverAttempts maximum number of failover attempts before
     *            exhausting. Use -1 to newer exhaust when round robin is also
     *            enabled. If round robin is disabled then it will exhaust when
     *            there are no more endpoints to failover
     * @param inheritErrorHandler whether or not to inherit error handler. If
     *            <tt>false</tt> then it will failover immediately in case of an
     *            exception
     * @param roundRobin whether or not to use round robin (which keeps state)
     * @param exceptions exception classes which we want to failover if one of
     *            them was thrown
     * @return the builder
     */
    public LoadBalanceDefinition failover(int maximumFailoverAttempts, boolean inheritErrorHandler, boolean roundRobin, Class<?>... exceptions) {
        return failover(maximumFailoverAttempts, inheritErrorHandler, roundRobin, false, exceptions);
    }

    /**
     * Uses fail over load balancer
     *
     * @param maximumFailoverAttempts maximum number of failover attempts before
     *            exhausting. Use -1 to newer exhaust when round robin is also
     *            enabled. If round robin is disabled then it will exhaust when
     *            there are no more endpoints to failover
     * @param inheritErrorHandler whether or not to inherit error handler. If
     *            <tt>false</tt> then it will failover immediately in case of an
     *            exception
     * @param roundRobin whether or not to use round robin (which keeps state)
     * @param sticky whether or not to use sticky (which keeps state)
     * @param exceptions exception classes which we want to failover if one of
     *            them was thrown
     * @return the builder
     */
    public LoadBalanceDefinition failover(int maximumFailoverAttempts, boolean inheritErrorHandler, boolean roundRobin, boolean sticky, Class<?>... exceptions) {
        FailoverLoadBalancerDefinition def = new FailoverLoadBalancerDefinition();
        def.setExceptionTypes(Arrays.asList(exceptions));
        def.setMaximumFailoverAttempts(Integer.toString(maximumFailoverAttempts));
        def.setRoundRobin(Boolean.toString(roundRobin));
        def.setSticky(Boolean.toString(sticky));
        setLoadBalancerType(def);
        this.setInheritErrorHandler(inheritErrorHandler);
        return this;
    }

    /**
     * Uses weighted load balancer
     *
     * @param roundRobin used to set the processor selection algorithm.
     * @param distributionRatio String of weighted ratios for distribution of
     *            messages.
     * @return the builder
     */
    public LoadBalanceDefinition weighted(boolean roundRobin, String distributionRatio) {
        return weighted(roundRobin, distributionRatio, ",");
    }

    /**
     * Uses weighted load balancer
     *
     * @param roundRobin used to set the processor selection algorithm.
     * @param distributionRatio String of weighted ratios for distribution of
     *            messages.
     * @param distributionRatioDelimiter String containing delimiter to be used
     *            for ratios
     * @return the builder
     */
    public LoadBalanceDefinition weighted(boolean roundRobin, String distributionRatio, String distributionRatioDelimiter) {
        WeightedLoadBalancerDefinition def = new WeightedLoadBalancerDefinition();
        def.setRoundRobin(Boolean.toString(roundRobin));
        def.setDistributionRatio(distributionRatio);
        def.setDistributionRatioDelimiter(distributionRatioDelimiter);
        setLoadBalancerType(def);
        return this;
    }

    /**
     * Uses round robin load balancer
     *
     * @return the builder
     */
    public LoadBalanceDefinition roundRobin() {
        setLoadBalancerType(new RoundRobinLoadBalancerDefinition());
        return this;
    }

    /**
     * Uses random load balancer
     *
     * @return the builder
     */
    public LoadBalanceDefinition random() {
        setLoadBalancerType(new RandomLoadBalancerDefinition());
        return this;
    }

    /**
     * Uses the custom load balancer
     *
     * @param ref reference to lookup a custom load balancer from the
     *            {@link org.apache.camel.spi.Registry} to be used.
     * @return the builder
     */
    public LoadBalanceDefinition custom(String ref) {
        CustomLoadBalancerDefinition balancer = new CustomLoadBalancerDefinition();
        balancer.setRef(ref);
        setLoadBalancerType(balancer);
        return this;
    }

    /**
     * Uses sticky load balancer
     *
     * @param correlationExpression the expression for correlation
     * @return the builder
     */
    public LoadBalanceDefinition sticky(Expression correlationExpression) {
        StickyLoadBalancerDefinition def = new StickyLoadBalancerDefinition();
        def.setCorrelationExpression(correlationExpression);
        setLoadBalancerType(def);
        return this;
    }

    /**
     * Uses topic load balancer
     * 
     * @return the builder
     */
    public LoadBalanceDefinition topic() {
        setLoadBalancerType(new TopicLoadBalancerDefinition());
        return this;
    }

    @Override
    public String getShortName() {
        return "loadBalance";
    }

    @Override
    public String getLabel() {
        return getOutputs().stream().map(ProcessorDefinition::getLabel)
                .collect(Collectors.joining(",", getShortName() + "[", "]"));
    }

    @Override
    public String toString() {
        return "LoadBalanceType[" + loadBalancerType + ", " + getOutputs() + "]";
    }
}
