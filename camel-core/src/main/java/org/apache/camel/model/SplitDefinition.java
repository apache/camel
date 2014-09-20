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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContextAware;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.processor.CamelInternalProcessor;
import org.apache.camel.processor.Splitter;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.AggregationStrategyBeanAdapter;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.CamelContextHelper;

/**
 * Represents an XML &lt;split/&gt; element
 *
 * @version 
 */
@XmlRootElement(name = "split")
@XmlAccessorType(XmlAccessType.FIELD)
public class SplitDefinition extends ExpressionNode implements ExecutorServiceAwareDefinition<SplitDefinition> {
    @XmlTransient
    private AggregationStrategy aggregationStrategy;
    @XmlTransient
    private ExecutorService executorService;
    @XmlAttribute
    private Boolean parallelProcessing;
    @XmlAttribute
    private String strategyRef;
    @XmlAttribute
    private String strategyMethodName;
    @XmlAttribute
    private Boolean strategyMethodAllowNull;
    @XmlAttribute
    private String executorServiceRef;
    @XmlAttribute
    private Boolean streaming;
    @XmlAttribute
    private Boolean stopOnException;
    @XmlAttribute
    private Long timeout;
    @XmlAttribute
    private String onPrepareRef;
    @XmlTransient
    private Processor onPrepare;
    @XmlAttribute
    private Boolean shareUnitOfWork;
    @XmlAttribute
    private Boolean parallelAggregate;

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
    public String getLabel() {
        return "split[" + getExpression() + "]";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Processor childProcessor = this.createChildProcessor(routeContext, true);
        aggregationStrategy = createAggregationStrategy(routeContext);

        boolean shutdownThreadPool = ProcessorDefinitionHelper.willCreateNewThreadPool(routeContext, this, isParallelProcessing());
        ExecutorService threadPool = ProcessorDefinitionHelper.getConfiguredExecutorService(routeContext, "Split", this, isParallelProcessing());

        long timeout = getTimeout() != null ? getTimeout() : 0;
        if (timeout > 0 && !isParallelProcessing()) {
            throw new IllegalArgumentException("Timeout is used but ParallelProcessing has not been enabled.");
        }
        if (onPrepareRef != null) {
            onPrepare = CamelContextHelper.mandatoryLookup(routeContext.getCamelContext(), onPrepareRef, Processor.class);
        }

        Expression exp = getExpression().createExpression(routeContext);

        Splitter answer = new Splitter(routeContext.getCamelContext(), exp, childProcessor, aggregationStrategy,
                            isParallelProcessing(), threadPool, shutdownThreadPool, isStreaming(), isStopOnException(),
                            timeout, onPrepare, isShareUnitOfWork(), isParallelAggregate());
        if (isShareUnitOfWork()) {
            // wrap answer in a sub unit of work, since we share the unit of work
            CamelInternalProcessor internalProcessor = new CamelInternalProcessor(answer);
            internalProcessor.addAdvice(new CamelInternalProcessor.SubUnitOfWorkProcessorAdvice());
            return internalProcessor;
        }
        return answer;
    }

    private AggregationStrategy createAggregationStrategy(RouteContext routeContext) {
        AggregationStrategy strategy = getAggregationStrategy();
        if (strategy == null && strategyRef != null) {
            Object aggStrategy = routeContext.lookup(strategyRef, Object.class);
            if (aggStrategy instanceof AggregationStrategy) {
                strategy = (AggregationStrategy) aggStrategy;
            } else if (aggStrategy != null) {
                AggregationStrategyBeanAdapter adapter = new AggregationStrategyBeanAdapter(aggStrategy, getStrategyMethodName());
                if (getStrategyMethodAllowNull() != null) {
                    adapter.setAllowNullNewExchange(getStrategyMethodAllowNull());
                    adapter.setAllowNullOldExchange(getStrategyMethodAllowNull());
                }
                strategy = adapter;
            } else {
                throw new IllegalArgumentException("Cannot find AggregationStrategy in Registry with name: " + strategyRef);
            }
        }

        if (strategy != null && strategy instanceof CamelContextAware) {
            ((CamelContextAware) strategy).setCamelContext(routeContext.getCamelContext());
        }

        return strategy;
    }

    // Fluent API
    // -------------------------------------------------------------------------

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
     * Set the aggregationStrategy
     *
     * @param aggregationStrategyRef a reference to a strategy to lookup
     * @return the builder
     */
    public SplitDefinition aggregationStrategyRef(String aggregationStrategyRef) {
        setStrategyRef(aggregationStrategyRef);
        return this;
    }

    /**
     * Sets the method name to use when using a POJO as {@link AggregationStrategy}.
     *
     * @param  methodName the method name to call
     * @return the builder
     */
    public SplitDefinition aggregationStrategyMethodName(String methodName) {
        setStrategyMethodName(methodName);
        return this;
    }

    /**
     * Sets allowing null when using a POJO as {@link AggregationStrategy}.
     *
     * @return the builder
     */
    public SplitDefinition aggregationStrategyMethodAllowNull() {
        setStrategyMethodAllowNull(true);
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
     * Doing the aggregate work in parallel
     * <p/>
     * Notice that if enabled, then the {@link org.apache.camel.processor.aggregate.AggregationStrategy} in use
     * must be implemented as thread safe, as concurrent threads can call the <tt>aggregate</tt> methods at the same time.
     *
     * @return the builder
     */
    public SplitDefinition parallelAggregate() {
        setParallelAggregate(true);
        return this;
    }

    /**
     * Enables streaming. 
     * See {@link org.apache.camel.model.SplitDefinition#isStreaming()} for more information
     *
     * @return the builder
     */
    public SplitDefinition streaming() {
        setStreaming(true);
        return this;
    }
    
    /**
     * Will now stop further processing if an exception or failure occurred during processing of an
     * {@link org.apache.camel.Exchange} and the caused exception will be thrown.
     * <p/>
     * Will also stop if processing the exchange failed (has a fault message) or an exception
     * was thrown and handled by the error handler (such as using onException). In all situations
     * the splitter will stop further processing. This is the same behavior as in pipeline, which
     * is used by the routing engine.
     * <p/>
     * The default behavior is to <b>not</b> stop but continue processing till the end
     *
     * @return the builder
     */
    public SplitDefinition stopOnException() {
        setStopOnException(true);
        return this;
    }
   
    public SplitDefinition executorService(ExecutorService executorService) {
        setExecutorService(executorService);
        return this;
    }
    
    public SplitDefinition executorServiceRef(String executorServiceRef) {
        setExecutorServiceRef(executorServiceRef);
        return this;
    }

    /**
     * Uses the {@link Processor} when preparing the {@link org.apache.camel.Exchange} to be send.
     * This can be used to deep-clone messages that should be send, or any custom logic needed before
     * the exchange is send.
     *
     * @param onPrepare the processor
     * @return the builder
     */
    public SplitDefinition onPrepare(Processor onPrepare) {
        setOnPrepare(onPrepare);
        return this;
    }

    /**
     * Uses the {@link Processor} when preparing the {@link org.apache.camel.Exchange} to be send.
     * This can be used to deep-clone messages that should be send, or any custom logic needed before
     * the exchange is send.
     *
     * @param onPrepareRef reference to the processor to lookup in the {@link org.apache.camel.spi.Registry}
     * @return the builder
     */
    public SplitDefinition onPrepareRef(String onPrepareRef) {
        setOnPrepareRef(onPrepareRef);
        return this;
    }

    /**
     * Sets a timeout value in millis to use when using parallelProcessing.
     *
     * @param timeout timeout in millis
     * @return the builder
     */
    public SplitDefinition timeout(long timeout) {
        setTimeout(timeout);
        return this;
    }

    /**
     * Shares the {@link org.apache.camel.spi.UnitOfWork} with the parent and each of the sub messages.
     *
     * @return the builder.
     * @see org.apache.camel.spi.SubUnitOfWork
     */
    public SplitDefinition shareUnitOfWork() {
        setShareUnitOfWork(true);
        return this;
    }

    // Properties
    //-------------------------------------------------------------------------

    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }

    public Boolean getParallelProcessing() {
        return parallelProcessing;
    }

    public void setParallelProcessing(Boolean parallelProcessing) {
        this.parallelProcessing = parallelProcessing;
    }

    public boolean isParallelProcessing() {
        return parallelProcessing != null && parallelProcessing;
    }

    public Boolean getStreaming() {
        return streaming;
    }

    public void setStreaming(Boolean streaming) {
        this.streaming = streaming;
    }

    /**
     * The splitter should use streaming -- exchanges are being sent as the data for them becomes available.
     * This improves throughput and memory usage, but it has a drawback:
     * - the sent exchanges will no longer contain the {@link org.apache.camel.Exchange#SPLIT_SIZE} header property
     *
     * @return whether or not streaming should be used
     */
    public boolean isStreaming() {
        return streaming != null && streaming;
    }

    public Boolean getParallelAggregate() {
        return parallelAggregate;
    }

    /**
     * Whether to aggregate using a sequential single thread, or allow parallel aggregation.
     * <p/>
     * Notice that if enabled, then the {@link org.apache.camel.processor.aggregate.AggregationStrategy} in use
     * must be implemented as thread safe, as concurrent threads can call the <tt>aggregate</tt> methods at the same time.
     */
    public boolean isParallelAggregate() {
        return parallelAggregate != null && parallelAggregate;
    }

    public void setParallelAggregate(Boolean parallelAggregate) {
        this.parallelAggregate = parallelAggregate;
    }

    public Boolean getStopOnException() {
        return stopOnException;
    }

    public void setStopOnException(Boolean stopOnException) {
        this.stopOnException = stopOnException;
    }

    public Boolean isStopOnException() {
        return stopOnException != null && stopOnException;
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

    public String getStrategyMethodName() {
        return strategyMethodName;
    }

    public void setStrategyMethodName(String strategyMethodName) {
        this.strategyMethodName = strategyMethodName;
    }

    public Boolean getStrategyMethodAllowNull() {
        return strategyMethodAllowNull;
    }

    public void setStrategyMethodAllowNull(Boolean strategyMethodAllowNull) {
        this.strategyMethodAllowNull = strategyMethodAllowNull;
    }

    public String getExecutorServiceRef() {
        return executorServiceRef;
    }

    public void setExecutorServiceRef(String executorServiceRef) {
        this.executorServiceRef = executorServiceRef;
    }

    public Long getTimeout() {
        return timeout;
    }

    public void setTimeout(Long timeout) {
        this.timeout = timeout;
    }

    public String getOnPrepareRef() {
        return onPrepareRef;
    }

    public void setOnPrepareRef(String onPrepareRef) {
        this.onPrepareRef = onPrepareRef;
    }

    public Processor getOnPrepare() {
        return onPrepare;
    }

    public void setOnPrepare(Processor onPrepare) {
        this.onPrepare = onPrepare;
    }

    public Boolean getShareUnitOfWork() {
        return shareUnitOfWork;
    }

    public void setShareUnitOfWork(Boolean shareUnitOfWork) {
        this.shareUnitOfWork = shareUnitOfWork;
    }

    public boolean isShareUnitOfWork() {
        return shareUnitOfWork != null && shareUnitOfWork;
    }
}
