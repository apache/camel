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
import java.util.Arrays;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.model.loadbalancer.CircuitBreakerLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.CustomLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.FailoverLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.RandomLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.RoundRobinLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.StickyLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.TopicLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.WeightedLoadBalancerDefinition;
import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.CollectionStringBuffer;

/**
 * Balances message processing among a number of nodes
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "loadBalance")
@XmlAccessorType(XmlAccessType.FIELD)
public class LoadBalanceDefinition extends ProcessorDefinition<LoadBalanceDefinition> {
    @XmlElements({
            @XmlElement(required = false, name = "failover", type = FailoverLoadBalancerDefinition.class),
            @XmlElement(required = false, name = "random", type = RandomLoadBalancerDefinition.class),
            // TODO: Camel 3.0 - Should be named customLoadBalancer to avoid naming clash with custom dataformat
            @XmlElement(required = false, name = "custom", type = CustomLoadBalancerDefinition.class),
            @XmlElement(required = false, name = "roundRobin", type = RoundRobinLoadBalancerDefinition.class),
            @XmlElement(required = false, name = "sticky", type = StickyLoadBalancerDefinition.class),
            @XmlElement(required = false, name = "topic", type = TopicLoadBalancerDefinition.class),
            @XmlElement(required = false, name = "weighted", type = WeightedLoadBalancerDefinition.class),
            @XmlElement(required = false, name = "circuitBreaker", type = CircuitBreakerLoadBalancerDefinition.class)}
        )
    private LoadBalancerDefinition loadBalancerType;
    @XmlElementRef
    private List<ProcessorDefinition<?>> outputs = new ArrayList<ProcessorDefinition<?>>();

    public LoadBalanceDefinition() {
    }

    @Override
    public List<ProcessorDefinition<?>> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<ProcessorDefinition<?>> outputs) {
        this.outputs = outputs;
        if (outputs != null) {
            for (ProcessorDefinition<?> output : outputs) {
                configureChild(output);
            }
        }
    }

    public boolean isOutputSupported() {
        return true;
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

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        // the load balancer is stateful so we should only create it once in case its used from a context scoped error handler

        LoadBalancer loadBalancer = loadBalancerType.getLoadBalancer(routeContext);
        if (loadBalancer == null) {
            // then create it and reuse it
            loadBalancer = loadBalancerType.createLoadBalancer(routeContext);
            loadBalancerType.setLoadBalancer(loadBalancer);

            // some load balancer can only support a fixed number of outputs
            int max = loadBalancerType.getMaximumNumberOfOutputs();
            int size = getOutputs().size();
            if (size > max) {
                throw new IllegalArgumentException("To many outputs configured on " + loadBalancerType + ": " + size + " > " + max);
            }

            for (ProcessorDefinition<?> processorType : getOutputs()) {
                // output must not be another load balancer
                // check for instanceof as the code below as there is compilation errors on earlier versions of JDK6
                // on Windows boxes or with IBM JDKs etc.
                if (LoadBalanceDefinition.class.isInstance(processorType)) {
                    throw new IllegalArgumentException("Loadbalancer already configured to: " + loadBalancerType + ". Cannot set it to: " + processorType);
                }
                Processor processor = createProcessor(routeContext, processorType);
                processor = wrapChannel(routeContext, processor, processorType);
                loadBalancer.addProcessor(processor);
            }
        }

        Boolean inherit = inheritErrorHandler;
        if (loadBalancerType instanceof FailoverLoadBalancerDefinition) {
            // special for failover load balancer where you can configure it to not inherit error handler for its children
            // but the load balancer itself should inherit so Camels error handler can react afterwards
            inherit = true;
        }
        Processor target = wrapChannel(routeContext, loadBalancer, this, inherit);
        return target;
    }
    
    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Uses a custom load balancer
     *
     * @param loadBalancer  the load balancer
     * @return the builder
     */
    public LoadBalanceDefinition loadBalance(LoadBalancer loadBalancer) {
        CustomLoadBalancerDefinition def = new CustomLoadBalancerDefinition();
        def.setLoadBalancer(loadBalancer);
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
     * @param exceptions exception classes which we want to failover if one of them was thrown
     * @return the builder
     */
    public LoadBalanceDefinition failover(Class<?>... exceptions) {
        return failover(-1, true, false, exceptions);
    }

    /**
     * Uses fail over load balancer
     *
     * @param maximumFailoverAttempts  maximum number of failover attempts before exhausting.
     *                                 Use -1 to newer exhaust when round robin is also enabled.
     *                                 If round robin is disabled then it will exhaust when there are no more endpoints to failover
     * @param inheritErrorHandler      whether or not to inherit error handler.
     *                                 If <tt>false</tt> then it will failover immediately in case of an exception
     * @param roundRobin               whether or not to use round robin (which keeps state)
     * @param exceptions               exception classes which we want to failover if one of them was thrown
     * @return the builder
     */
    public LoadBalanceDefinition failover(int maximumFailoverAttempts, boolean inheritErrorHandler, boolean roundRobin, Class<?>... exceptions) {
        return failover(maximumFailoverAttempts, inheritErrorHandler, roundRobin, false, exceptions);
    }

    /**
     * Uses fail over load balancer
     *
     * @param maximumFailoverAttempts  maximum number of failover attempts before exhausting.
     *                                 Use -1 to newer exhaust when round robin is also enabled.
     *                                 If round robin is disabled then it will exhaust when there are no more endpoints to failover
     * @param inheritErrorHandler      whether or not to inherit error handler.
     *                                 If <tt>false</tt> then it will failover immediately in case of an exception
     * @param roundRobin               whether or not to use round robin (which keeps state)
     * @param sticky                   whether or not to use sticky (which keeps state)
     * @param exceptions               exception classes which we want to failover if one of them was thrown
     * @return the builder
     */
    public LoadBalanceDefinition failover(int maximumFailoverAttempts, boolean inheritErrorHandler, boolean roundRobin, boolean sticky, Class<?>... exceptions) {
        FailoverLoadBalancerDefinition def = new FailoverLoadBalancerDefinition();
        def.setExceptionTypes(Arrays.asList(exceptions));
        def.setMaximumFailoverAttempts(maximumFailoverAttempts);
        def.setRoundRobin(roundRobin);
        def.setSticky(sticky);
        setLoadBalancerType(def);
        this.setInheritErrorHandler(inheritErrorHandler);
        return this;
    }

    /**
     * Uses weighted load balancer
     *
     * @param roundRobin                   used to set the processor selection algorithm.
     * @param distributionRatio            String of weighted ratios for distribution of messages.
     * @return the builder
     */
    public LoadBalanceDefinition weighted(boolean roundRobin, String distributionRatio) {
        return weighted(roundRobin, distributionRatio, ",");
    }

    /**
     * Uses circuitBreaker load balancer
     *
     * @param threshold         number of errors before failure.
     * @param halfOpenAfter     time interval in milliseconds for half open state.
     * @param exceptions        exception classes which we want to break if one of them was thrown
     * @return the builder
     * @deprecated use Hystrix EIP instead which is the popular Netflix implementation of circuit breaker
     */
    @Deprecated
    public LoadBalanceDefinition circuitBreaker(int threshold, long halfOpenAfter, Class<?>... exceptions) {
        CircuitBreakerLoadBalancerDefinition def = new CircuitBreakerLoadBalancerDefinition();
        def.setExceptionTypes(Arrays.asList(exceptions));
        def.setThreshold(threshold);
        def.setHalfOpenAfter(halfOpenAfter);
        setLoadBalancerType(def);
        return this;
    }
    
    /**
     * Uses weighted load balancer
     *
     * @param roundRobin                   used to set the processor selection algorithm.
     * @param distributionRatio            String of weighted ratios for distribution of messages.
     * @param distributionRatioDelimiter   String containing delimiter to be used for ratios
     * @return the builder
     */
    public LoadBalanceDefinition weighted(boolean roundRobin, String distributionRatio, String distributionRatioDelimiter) {
        WeightedLoadBalancerDefinition def = new WeightedLoadBalancerDefinition();
        def.setRoundRobin(roundRobin);
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
     * @param ref reference to lookup a custom load balancer from the {@link org.apache.camel.spi.Registry} to be used.
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
     * @param correlationExpression  the expression for correlation
     * @return  the builder
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
    public String getLabel() {
        CollectionStringBuffer buffer = new CollectionStringBuffer("loadBalance[");
        List<ProcessorDefinition<?>> list = getOutputs();
        for (ProcessorDefinition<?> processorType : list) {
            buffer.append(processorType.getLabel());
        }
        buffer.append("]");
        return buffer.toString();
    }

    @Override
    public String toString() {
        return "LoadBalanceType[" + loadBalancerType + ", " + getOutputs() + "]";
    }
}
