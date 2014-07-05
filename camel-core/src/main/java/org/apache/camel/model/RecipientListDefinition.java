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
import java.util.List;
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
import org.apache.camel.processor.EvaluateExpressionProcessor;
import org.apache.camel.processor.Pipeline;
import org.apache.camel.processor.RecipientList;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.AggregationStrategyBeanAdapter;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.CamelContextHelper;

/**
 * Represents an XML &lt;recipientList/&gt; element
 *
 * @version 
 */
@XmlRootElement(name = "recipientList")
@XmlAccessorType(XmlAccessType.FIELD)
public class RecipientListDefinition<Type extends ProcessorDefinition<Type>> extends NoOutputExpressionNode implements ExecutorServiceAwareDefinition<RecipientListDefinition<Type>> {
    @XmlTransient
    private AggregationStrategy aggregationStrategy;
    @XmlTransient
    private ExecutorService executorService;
    @XmlAttribute
    private String delimiter;
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
    private Boolean stopOnException;
    @XmlAttribute
    private Boolean ignoreInvalidEndpoints;
    @XmlAttribute
    private Boolean streaming;
    @XmlAttribute
    private Long timeout;
    @XmlAttribute
    private String onPrepareRef;
    @XmlTransient
    private Processor onPrepare;
    @XmlAttribute
    private Boolean shareUnitOfWork;
    @XmlAttribute
    private Integer cacheSize;
    @XmlAttribute
    private Boolean parallelAggregate;

    public RecipientListDefinition() {
    }

    public RecipientListDefinition(ExpressionDefinition expression) {
        super(expression);
    }

    public RecipientListDefinition(Expression expression) {
        super(expression);
    }

    @Override
    public String toString() {
        return "RecipientList[" + getExpression() + "]";
    }

    @Override
    public String getShortName() {
        return "recipientList";
    }
    
    @Override
    public String getLabel() {
        return "recipientList[" + getExpression() + "]";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        final Expression expression = getExpression().createExpression(routeContext);

        RecipientList answer;
        if (delimiter != null) {
            answer = new RecipientList(routeContext.getCamelContext(), expression, delimiter);
        } else {
            answer = new RecipientList(routeContext.getCamelContext(), expression);
        }
        answer.setAggregationStrategy(createAggregationStrategy(routeContext));
        answer.setParallelProcessing(isParallelProcessing());
        answer.setParallelAggregate(isParallelAggregate());
        answer.setStreaming(isStreaming());
        answer.setShareUnitOfWork(isShareUnitOfWork());
        if (getCacheSize() != null) {
            answer.setCacheSize(getCacheSize());
        }
        if (onPrepareRef != null) {
            onPrepare = CamelContextHelper.mandatoryLookup(routeContext.getCamelContext(), onPrepareRef, Processor.class);
        }
        if (onPrepare != null) {
            answer.setOnPrepare(onPrepare);
        }
        if (stopOnException != null) {
            answer.setStopOnException(isStopOnException());
        }
        if (ignoreInvalidEndpoints != null) {
            answer.setIgnoreInvalidEndpoints(ignoreInvalidEndpoints);
        }
        if (getTimeout() != null) {
            answer.setTimeout(getTimeout());
        }

        boolean shutdownThreadPool = ProcessorDefinitionHelper.willCreateNewThreadPool(routeContext, this, isParallelProcessing());
        ExecutorService threadPool = ProcessorDefinitionHelper.getConfiguredExecutorService(routeContext, "RecipientList", this, isParallelProcessing());
        answer.setExecutorService(threadPool);
        answer.setShutdownExecutorService(shutdownThreadPool);
        long timeout = getTimeout() != null ? getTimeout() : 0;
        if (timeout > 0 && !isParallelProcessing()) {
            throw new IllegalArgumentException("Timeout is used but ParallelProcessing has not been enabled.");
        }

        // create a pipeline with two processors
        // the first is the eval processor which evaluates the expression to use
        // the second is the recipient list
        List<Processor> pipe = new ArrayList<Processor>(2);

        // the eval processor must be wrapped in error handler, so in case there was an
        // error during evaluation, the error handler can deal with it
        // the recipient list is not in error handler, as its has its own special error handling
        // when sending to the recipients individually
        Processor evalProcessor = new EvaluateExpressionProcessor(expression);
        evalProcessor = super.wrapInErrorHandler(routeContext, evalProcessor);

        pipe.add(evalProcessor);
        pipe.add(answer);

        // wrap in nested pipeline so this appears as one processor
        // (threads definition does this as well)
        return new Pipeline(routeContext.getCamelContext(), pipe) {
            @Override
            public String toString() {
                return "RecipientList[" + expression + "]";
            }
        };
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
        if (strategy == null) {
            // fallback to use latest
            strategy = new UseLatestAggregationStrategy();
        }

        if (strategy instanceof CamelContextAware) {
            ((CamelContextAware) strategy).setCamelContext(routeContext.getCamelContext());
        }

        return strategy;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    @Override
    @SuppressWarnings("unchecked")
    public Type end() {
        // allow end() to return to previous type so you can continue in the DSL
        return (Type) super.end();
    }

    /**
     * Set the delimiter
     *
     * @param delimiter the delimiter
     * @return the builder
     */
    public RecipientListDefinition<Type> delimiter(String delimiter) {
        setDelimiter(delimiter);
        return this;
    }

    /**
     * Set the aggregationStrategy
     *
     * @param aggregationStrategy the strategy
     * @return the builder
     */
    public RecipientListDefinition<Type> aggregationStrategy(AggregationStrategy aggregationStrategy) {
        setAggregationStrategy(aggregationStrategy);
        return this;
    }

    /**
     * Set the aggregationStrategy
     *
     * @param aggregationStrategyRef a reference to a strategy to lookup
     * @return the builder
     */
    public RecipientListDefinition<Type> aggregationStrategyRef(String aggregationStrategyRef) {
        setStrategyRef(aggregationStrategyRef);
        return this;
    }

    /**
     * Sets the method name to use when using a POJO as {@link AggregationStrategy}.
     *
     * @param  methodName the method name to call
     * @return the builder
     */
    public RecipientListDefinition<Type> aggregationStrategyMethodName(String methodName) {
        setStrategyMethodName(methodName);
        return this;
    }

    /**
     * Sets allowing null when using a POJO as {@link AggregationStrategy}.
     *
     * @return the builder
     */
    public RecipientListDefinition<Type> aggregationStrategyMethodAllowNull() {
        setStrategyMethodAllowNull(true);
        return this;
    }

    /**
     * Ignore the invalidate endpoint exception when try to create a producer with that endpoint
     *
     * @return the builder
     */
    public RecipientListDefinition<Type> ignoreInvalidEndpoints() {
        setIgnoreInvalidEndpoints(true);
        return this;
    }

    /**
     * Doing the recipient list work in parallel
     *
     * @return the builder
     */
    public RecipientListDefinition<Type> parallelProcessing() {
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
    public RecipientListDefinition<Type> parallelAggregate() {
        setParallelAggregate(true);
        return this;
    }

    /**
     * Doing the recipient list work in streaming model
     *
     * @return the builder
     */
    public RecipientListDefinition<Type> streaming() {
        setStreaming(true);
        return this;
    }

    /**
     * Will now stop further processing if an exception or failure occurred during processing of an
     * {@link org.apache.camel.Exchange} and the caused exception will be thrown.
     * <p/>
     * Will also stop if processing the exchange failed (has a fault message) or an exception
     * was thrown and handled by the error handler (such as using onException). In all situations
     * the recipient list will stop further processing. This is the same behavior as in pipeline, which
     * is used by the routing engine.
     * <p/>
     * The default behavior is to <b>not</b> stop but continue processing till the end
     *
     * @return the builder
     */
    public RecipientListDefinition<Type> stopOnException() {
        setStopOnException(true);
        return this;
    }

    public RecipientListDefinition<Type> executorService(ExecutorService executorService) {
        setExecutorService(executorService);
        return this;
    }

    public RecipientListDefinition<Type> executorServiceRef(String executorServiceRef) {
        setExecutorServiceRef(executorServiceRef);
        return this;
    }

    /**
     * Uses the {@link Processor} when preparing the {@link org.apache.camel.Exchange} to be used send.
     * This can be used to deep-clone messages that should be send, or any custom logic needed before
     * the exchange is send.
     *
     * @param onPrepare the processor
     * @return the builder
     */
    public RecipientListDefinition<Type> onPrepare(Processor onPrepare) {
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
    public RecipientListDefinition<Type> onPrepareRef(String onPrepareRef) {
        setOnPrepareRef(onPrepareRef);
        return this;
    }

    /**
     * Sets a timeout value in millis to use when using parallelProcessing.
     *
     * @param timeout timeout in millis
     * @return the builder
     */
    public RecipientListDefinition<Type> timeout(long timeout) {
        setTimeout(timeout);
        return this;
    }

    /**
     * Shares the {@link org.apache.camel.spi.UnitOfWork} with the parent and each of the sub messages.
     *
     * @return the builder.
     * @see org.apache.camel.spi.SubUnitOfWork
     */
    public RecipientListDefinition<Type> shareUnitOfWork() {
        setShareUnitOfWork(true);
        return this;
    }

    /**
     * Sets the maximum size used by the {@link org.apache.camel.impl.ProducerCache} which is used
     * to cache and reuse producers when using this recipient list, when uris are reused.
     *
     * @param cacheSize  the cache size, use <tt>0</tt> for default cache size, or <tt>-1</tt> to turn cache off.
     * @return the builder
     */
    public RecipientListDefinition<Type> cacheSize(int cacheSize) {
        setCacheSize(cacheSize);
        return this;
    }

    // Properties
    //-------------------------------------------------------------------------

    public String getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
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

    public Boolean getIgnoreInvalidEndpoints() {
        return ignoreInvalidEndpoints;
    }

    public void setIgnoreInvalidEndpoints(Boolean ignoreInvalidEndpoints) {
        this.ignoreInvalidEndpoints = ignoreInvalidEndpoints;
    }

    public boolean isIgnoreInvalidEndpoints() {
        return ignoreInvalidEndpoints != null && ignoreInvalidEndpoints;
    }

    public Boolean getStopOnException() {
        return stopOnException;
    }

    public void setStopOnException(Boolean stopOnException) {
        this.stopOnException = stopOnException;
    }

    public boolean isStopOnException() {
        return stopOnException != null && stopOnException;
    }

    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public Boolean getStreaming() {
        return streaming;
    }

    public void setStreaming(Boolean streaming) {
        this.streaming = streaming;
    }

    public boolean isStreaming() {
        return streaming != null && streaming;
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

    public Integer getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(Integer cacheSize) {
        this.cacheSize = cacheSize;
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
}
