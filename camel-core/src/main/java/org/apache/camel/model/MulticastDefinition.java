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
import java.util.concurrent.ExecutorService;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Processor;
import org.apache.camel.processor.MulticastProcessor;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.concurrent.ExecutorServiceHelper;

/**
 * Represents an XML &lt;multicast/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "multicast")
@XmlAccessorType(XmlAccessType.FIELD)
public class MulticastDefinition extends OutputDefinition<MulticastDefinition> implements ExecutorServiceAwareDefinition<MulticastDefinition> {
    @XmlAttribute(required = false)
    private Boolean parallelProcessing;
    @XmlAttribute(required = false)
    private String strategyRef;
    @XmlTransient
    private ExecutorService executorService;
    @XmlAttribute(required = false)
    private String executorServiceRef;
    @XmlAttribute(required = false)
    private Boolean streaming;
    @XmlAttribute(required = false)
    private Boolean stopOnException;
    @XmlTransient
    private AggregationStrategy aggregationStrategy;


    @Override
    public String toString() {
        return "Multicast[" + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "multicast";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        return this.createChildProcessor(routeContext, true);
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Set the multicasting aggregationStrategy
     *
     * @return the builder
     */
    public MulticastDefinition aggregationStrategy(AggregationStrategy aggregationStrategy) {
        setAggregationStrategy(aggregationStrategy);
        return this;
    }
    
    /**
     * Set the aggregationStrategy
     *
     * @param aggregationStrategyRef a reference to a strategy to lookup
     * @return the builder
     */
    public MulticastDefinition aggregationStrategyRef(String aggregationStrategyRef) {
        setStrategyRef(aggregationStrategyRef);
        return this;
    }

    /**
     * Uses the {@link java.util.concurrent.ExecutorService} to do the multicasting work
     *     
     * @return the builder
     */
    public MulticastDefinition parallelProcessing() {
        setParallelProcessing(true);
        return this;
    }
    
    /**
     * Aggregates the responses as the are done (e.g. out of order sequence)
     *
     * @return the builder
     */
    public MulticastDefinition streaming() {
        setStreaming(true);
        return this;
    }

    /**
     * Will now stop further processing if an exception occurred during processing of an
     * {@link org.apache.camel.Exchange} and the caused exception will be thrown.
     * <p/>
     * The default behavior is to <b>not</b> stop but continue processing till the end
     *
     * @return the builder
     */
    public MulticastDefinition stopOnException() {
        setStopOnException(true);
        return this;
    }
    
    public MulticastDefinition executorService(ExecutorService executorService) {
        setExecutorService(executorService);
        return this;
    }
    
    public MulticastDefinition executorServiceRef(String executorServiceRef) {
        setExecutorServiceRef(executorServiceRef);
        return this;
    }

    protected Processor createCompositeProcessor(RouteContext routeContext, List<Processor> list) throws Exception {
        if (strategyRef != null) {
            aggregationStrategy = routeContext.lookup(strategyRef, AggregationStrategy.class);
        }
        if (aggregationStrategy == null) {
            // default to use latest aggregation strategy
            aggregationStrategy = new UseLatestAggregationStrategy();
        }

        executorService = ExecutorServiceHelper.getConfiguredExecutorService(routeContext, "Multicast", this);
        if (isParallelProcessing() && executorService == null) {
            // we are running in parallel so create a cached thread pool which grows/shrinks automatic
            executorService = routeContext.getCamelContext().getExecutorServiceStrategy().newDefaultThreadPool(this, "Multicast");
        }

        return new MulticastProcessor(routeContext.getCamelContext(), list, aggregationStrategy, isParallelProcessing(),
                                      executorService, isStreaming(), isStopOnException());
    }

    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    public MulticastDefinition setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
        return this;
    }

    public boolean isParallelProcessing() {
        return parallelProcessing != null ? parallelProcessing : false;
    }

    public void setParallelProcessing(boolean parallelProcessing) {
        this.parallelProcessing = parallelProcessing;        
    }

    public boolean isStreaming() {
        return streaming != null ? streaming : false;
    }

    public void setStreaming(boolean streaming) {
        this.streaming = streaming;
    }

    public Boolean isStopOnException() {
        return stopOnException != null ? stopOnException : false;
    }

    public void setStopOnException(Boolean stopOnException) {
        this.stopOnException = stopOnException;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public String getStrategyRef() {
        return strategyRef;
    }

    public void setStrategyRef(String strategyRef) {
        this.strategyRef = strategyRef;
    }

    public String getExecutorServiceRef() {
        return executorServiceRef;
    }

    public void setExecutorServiceRef(String executorServiceRef) {
        this.executorServiceRef = executorServiceRef;
    }
}
