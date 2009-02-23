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

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.builder.ExpressionClause;
import org.apache.camel.model.language.ExpressionType;
import org.apache.camel.processor.Splitter;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.spi.RouteContext;

/**
 * Represents an XML &lt;split/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "split")
@XmlAccessorType(XmlAccessType.FIELD)
public class SplitterType extends ExpressionNode {
    @XmlTransient
    private AggregationStrategy aggregationStrategy;
    @XmlAttribute(required = false)
    private Boolean parallelProcessing;
    @XmlTransient
    private Executor executor;
    @XmlAttribute(required = false)
    private String strategyRef;
    @XmlAttribute(required = false)
    private String threadPoolExecutorRef;
    @XmlAttribute(required = false)
    private Boolean streaming = false;
    
    public SplitterType() {
    }

    public SplitterType(Expression expression) {
        super(expression);
    }

    public SplitterType(ExpressionType expression) {
        super(expression);
    }

    @Override
    public String toString() {
        return "Splitter[" + getExpression() + " -> " + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "splitter";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Processor childProcessor = routeContext.createProcessor(this);
        aggregationStrategy = createAggregationStrategy(routeContext);
        executor = createThreadPoolExecutor(routeContext);
        return new Splitter(getExpression().createExpression(routeContext), childProcessor, aggregationStrategy,
                isParallelProcessing(), executor, streaming);
    }

    
    private AggregationStrategy createAggregationStrategy(RouteContext routeContext) {
        AggregationStrategy strategy = getAggregationStrategy();
        if (strategy == null && strategyRef != null) {
            strategy = routeContext.lookup(strategyRef, AggregationStrategy.class);
        }
        if (strategy == null) {
            // fallback to use latest
            strategy = new UseLatestAggregationStrategy();
        }
        return strategy;
    }        
    
    private Executor createThreadPoolExecutor(RouteContext routeContext) {
        Executor executor = getExecutor();
        if (executor == null && threadPoolExecutorRef != null) {
            executor = routeContext.lookup(threadPoolExecutorRef, ThreadPoolExecutor.class);
        }
        if (executor == null) {
            // fall back and use default
            executor = new ThreadPoolExecutor(4, 16, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
        }
        return executor;
    }         
    
    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Set the expression that the splitter will use
     *
     * @return the builder
     */
    public ExpressionClause<SplitterType> expression() {
        return ExpressionClause.createAndSetExpression(this);
    }
    /**
     * Set the aggregationStrategy
     *
     * @return the builder
     */
    public SplitterType aggregationStrategy(AggregationStrategy aggregationStrategy) {
        setAggregationStrategy(aggregationStrategy);
        return this;
    }
    
    /**
     * Doing the splitting work in parallel
     *
     * @return the builder
     */
    public SplitterType parallelProcessing() {
        setParallelProcessing(true);
        return this;
    }
    
    /**
     * Set the splitting action's thread model
     *
     * @param parallelProcessing <tt>true</tt> to use a thread pool, if <tt>false</tt> then work is done in the
     * calling thread.
     *
     * @return the builder
     */
    public SplitterType parallelProcessing(boolean parallelProcessing) {
        setParallelProcessing(parallelProcessing);
        return this;
    }
    
    /**
     * Enables streaming. 
     * See {@link SplitterType#setStreaming(boolean)} for more information
     *
     * @return the builder
     */
    public SplitterType streaming() {
        setStreaming(true);
        return this;
    }
    
    /**
     * Setting the executor for executing the splitting action. 
     *
     * @param executor the executor
     * @return the builder
     */
    public SplitterType executor(Executor executor) {
        setExecutor(executor);
        return this;
    }
    
    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }
    
    public boolean isParallelProcessing() {
        return parallelProcessing != null ? parallelProcessing : false;
    }

    public void setParallelProcessing(boolean parallelProcessing) {
        this.parallelProcessing = parallelProcessing;
    }
    
    /**
     * The splitter should use streaming -- exchanges are being sent as the data for them becomes available.
     * This improves throughput and memory usage, but it has a drawback: 
     * - the sent exchanges will no longer contain the {@link Splitter#SPLIT_SIZE} header property 
     * 
     * @return whether or not streaming should be used
     */
    public boolean isStreaming() {
        return streaming != null ? streaming : false;
    }

    public void setStreaming(boolean streaming) {
        this.streaming = streaming;
    }  
    
    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }
}
