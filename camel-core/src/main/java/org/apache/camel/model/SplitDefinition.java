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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.builder.ExpressionClause;
import org.apache.camel.model.language.ExpressionDefinition;
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
public class SplitDefinition extends ExpressionNode {
    @XmlTransient
    private AggregationStrategy aggregationStrategy;
    @XmlTransient
    private ExecutorService executorService;
    @XmlAttribute(required = false)
    private Boolean parallelProcessing;
    @XmlAttribute(required = false)
    private String strategyRef;
    @XmlAttribute(required = false)
    private String executorServiceRef;
    @XmlAttribute(required = false)
    private Boolean streaming = false;
    @XmlAttribute(required = false)
    private Boolean stopOnException;

    public SplitDefinition() {
    }

    public SplitDefinition(Expression expression) {
        super(expression);
    }

    public SplitDefinition(ExpressionDefinition expression) {
        super(expression);
    }

    @Override
    public String toString() {
        return "Split[" + getExpression() + " -> " + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "split";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Processor childProcessor = routeContext.createProcessor(this);
        aggregationStrategy = createAggregationStrategy(routeContext);
        executorService = createExecutorService(routeContext);
        return new Splitter(getExpression().createExpression(routeContext), childProcessor, aggregationStrategy,
                isParallelProcessing(), executorService, isStreaming(), isStopOnException());
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
    
    private ExecutorService createExecutorService(RouteContext routeContext) {
        if (executorServiceRef != null) {
            executorService = routeContext.lookup(executorServiceRef, ExecutorService.class);
        }
        if (executorService == null) {
            // fall back and use default
            executorService = Executors.newScheduledThreadPool(5);
        }
        return executorService;
    }         
    
    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Set the expression that the splitter will use
     *
     * @return the builder
     */
    public ExpressionClause<SplitDefinition> expression() {
        return ExpressionClause.createAndSetExpression(this);
    }
    /**
     * Set the aggregationStrategy
     *
     * @return the builder
     */
    public SplitDefinition aggregationStrategy(AggregationStrategy aggregationStrategy) {
        setAggregationStrategy(aggregationStrategy);
        return this;
    }
    
    /**
     * Doing the splitting work in parallel
     *
     * @return the builder
     */
    public SplitDefinition parallelProcessing() {
        setParallelProcessing(true);
        return this;
    }
    
    /**
     * Set the splitting action's thread model
     *
     * @param parallelProcessing <tt>true</tt> to use a thread pool, if <tt>false</tt> then work is done in the
     * calling thread.
     *
     * @deprecated use #parallelProcessing instead
     * @return the builder
     */
    @Deprecated
    public SplitDefinition parallelProcessing(boolean parallelProcessing) {
        setParallelProcessing(parallelProcessing);
        return this;
    }
    
    /**
     * Enables streaming. 
     * See {@link SplitDefinition#setStreaming(boolean)} for more information
     *
     * @return the builder
     */
    public SplitDefinition streaming() {
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
    public SplitDefinition stopOnException() {
        setStopOnException(true);
        return this;
    }

    /**
     * Setting the executor service for executing the splitting action.
     *
     * @param executorService the executor service
     * @return the builder
     */
    public SplitDefinition executorService(ExecutorService executorService) {
        setExecutorService(executorService);
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
     * - the sent exchanges will no longer contain the {@link org.apache.camel.Exchange#SPLIT_SIZE} header property
     * 
     * @return whether or not streaming should be used
     */
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
}
