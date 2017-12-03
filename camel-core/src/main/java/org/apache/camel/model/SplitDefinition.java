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
import org.apache.camel.processor.Splitter;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.AggregationStrategyBeanAdapter;
import org.apache.camel.processor.aggregate.ShareUnitOfWorkAggregationStrategy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.CamelContextHelper;

/**
 * Splits a single message into many sub-messages.
 *
 * @version 
 */
@Metadata(label = "eip,routing")
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
    @XmlAttribute @Metadata(defaultValue = "0")
    private Long timeout;
    @XmlAttribute
    private String onPrepareRef;
    @XmlTransient
    private Processor onPrepare;
    @XmlAttribute
    private Boolean shareUnitOfWork;
    @XmlAttribute
    private Boolean parallelAggregate;
    @XmlAttribute
    private Boolean stopOnAggregateException;

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
    public String getLabel() {
        return "split[" + getExpression() + "]";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Processor childProcessor = this.createChildProcessor(routeContext, true);
        aggregationStrategy = createAggregationStrategy(routeContext);

        boolean isParallelProcessing = getParallelProcessing() != null && getParallelProcessing();
        boolean isStreaming = getStreaming() != null && getStreaming();
        boolean isShareUnitOfWork = getShareUnitOfWork() != null && getShareUnitOfWork();
        boolean isParallelAggregate = getParallelAggregate() != null && getParallelAggregate();
        boolean isStopOnAggregateException = getStopOnAggregateException() != null && getStopOnAggregateException();
        boolean shutdownThreadPool = ProcessorDefinitionHelper.willCreateNewThreadPool(routeContext, this, isParallelProcessing);
        ExecutorService threadPool = ProcessorDefinitionHelper.getConfiguredExecutorService(routeContext, "Split", this, isParallelProcessing);

        long timeout = getTimeout() != null ? getTimeout() : 0;
        if (timeout > 0 && !isParallelProcessing) {
            throw new IllegalArgumentException("Timeout is used but ParallelProcessing has not been enabled.");
        }
        if (onPrepareRef != null) {
            onPrepare = CamelContextHelper.mandatoryLookup(routeContext.getCamelContext(), onPrepareRef, Processor.class);
        }

        Expression exp = getExpression().createExpression(routeContext);

        Splitter answer = new Splitter(routeContext.getCamelContext(), exp, childProcessor, aggregationStrategy,
                            isParallelProcessing, threadPool, shutdownThreadPool, isStreaming, isStopOnException(),
                            timeout, onPrepare, isShareUnitOfWork, isParallelAggregate, isStopOnAggregateException);
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

        if (strategy instanceof CamelContextAware) {
            ((CamelContextAware) strategy).setCamelContext(routeContext.getCamelContext());
        }

        if (strategy != null && shareUnitOfWork != null && shareUnitOfWork) {
            // wrap strategy in share unit of work
            strategy = new ShareUnitOfWorkAggregationStrategy(strategy);
        }

        return strategy;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Sets the AggregationStrategy to be used to assemble the replies from the splitted messages, into a single outgoing message from the Splitter.
     * By default Camel will use the original incoming message to the splitter (leave it unchanged). You can also use a POJO as the AggregationStrategy
     */
    public SplitDefinition aggregationStrategy(AggregationStrategy aggregationStrategy) {
        setAggregationStrategy(aggregationStrategy);
        return this;
    }

    /**
     * Sets a reference to the AggregationStrategy to be used to assemble the replies from the splitted messages, into a single outgoing message from the Splitter.
     * By default Camel will use the original incoming message to the splitter (leave it unchanged). You can also use a POJO as the AggregationStrategy
     */
    public SplitDefinition aggregationStrategyRef(String aggregationStrategyRef) {
        setStrategyRef(aggregationStrategyRef);
        return this;
    }

    /**
     * This option can be used to explicit declare the method name to use, when using POJOs as the AggregationStrategy.
     *
     * @param  methodName the method name to call
     * @return the builder
     */
    public SplitDefinition aggregationStrategyMethodName(String methodName) {
        setStrategyMethodName(methodName);
        return this;
    }

    /**
     * If this option is false then the aggregate method is not used if there was no data to enrich.
     * If this option is true then null values is used as the oldExchange (when no data to enrich), when using POJOs as the AggregationStrategy
     *
     * @return the builder
     */
    public SplitDefinition aggregationStrategyMethodAllowNull() {
        setStrategyMethodAllowNull(true);
        return this;
    }

    /**
     * If enabled then processing each splitted messages occurs concurrently.
     * Note the caller thread will still wait until all messages has been fully processed, before it continues.
     * Its only processing the sub messages from the splitter which happens concurrently.
     *
     * @return the builder
     */
    public SplitDefinition parallelProcessing() {
        setParallelProcessing(true);
        return this;
    }

    /**
     * If enabled then processing each splitted messages occurs concurrently.
     * Note the caller thread will still wait until all messages has been fully processed, before it continues.
     * Its only processing the sub messages from the splitter which happens concurrently.
     *
     * @return the builder
     */
    public SplitDefinition parallelProcessing(boolean parallelProcessing) {
        setParallelProcessing(parallelProcessing);
        return this;
    }

    /**
     * If enabled then the aggregate method on AggregationStrategy can be called concurrently.
     * Notice that this would require the implementation of AggregationStrategy to be implemented as thread-safe.
     * By default this is false meaning that Camel synchronizes the call to the aggregate method.
     * Though in some use-cases this can be used to archive higher performance when the AggregationStrategy is implemented as thread-safe.
     *
     * @return the builder
     */
    public SplitDefinition parallelAggregate() {
        setParallelAggregate(true);
        return this;
    }
    
    /**
     * If enabled, unwind exceptions occurring at aggregation time to the error handler when parallelProcessing is used.
     * Currently, aggregation time exceptions do not stop the route processing when parallelProcessing is used.
     * Enabling this option allows to work around this behavior.
     *
     * The default value is <code>false</code> for the sake of backward compatibility.
     *
     * @return the builder
     */
    public SplitDefinition stopOnAggregateException() {
        setStopOnAggregateException(true);
        return this;
    }

    /**
     * When in streaming mode, then the splitter splits the original message on-demand, and each splitted
     * message is processed one by one. This reduces memory usage as the splitter do not split all the messages first,
     * but then we do not know the total size, and therefore the {@link org.apache.camel.Exchange#SPLIT_SIZE} is empty.
     * <p/>
     * In non-streaming mode (default) the splitter will split each message first, to know the total size, and then
     * process each message one by one. This requires to keep all the splitted messages in memory and therefore requires
     * more memory. The total size is provided in the {@link org.apache.camel.Exchange#SPLIT_SIZE} header.
     * <p/>
     * The streaming mode also affects the aggregation behavior.
     * If enabled then Camel will process replies out-of-order, eg in the order they come back.
     * If disabled, Camel will process replies in the same order as the messages was splitted.
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

    /**
     * To use a custom Thread Pool to be used for parallel processing.
     * Notice if you set this option, then parallel processing is automatic implied, and you do not have to enable that option as well.
     */
    public SplitDefinition executorService(ExecutorService executorService) {
        setExecutorService(executorService);
        return this;
    }

    /**
     * Refers to a custom Thread Pool to be used for parallel processing.
     * Notice if you set this option, then parallel processing is automatic implied, and you do not have to enable that option as well.
     */
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
     * Sets a total timeout specified in millis, when using parallel processing.
     * If the Splitter hasn't been able to split and process all the sub messages within the given timeframe,
     * then the timeout triggers and the Splitter breaks out and continues.
     * Notice if you provide a TimeoutAwareAggregationStrategy then the timeout method is invoked before breaking out.
     * If the timeout is reached with running tasks still remaining, certain tasks for which it is difficult for Camel
     * to shut down in a graceful manner may continue to run. So use this option with a bit of care.
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
     * Splitter will by default not share unit of work between the parent exchange and each splitted exchange.
     * This means each splitted exchange has its own individual unit of work.
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

    /**
     * Expression of how to split the message body, such as as-is, using a tokenizer, or using an xpath.
     */
    @Override
    public void setExpression(ExpressionDefinition expression) {
        // override to include javadoc what the expression is used for
        super.setExpression(expression);
    }

    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    /**
     * Sets the AggregationStrategy to be used to assemble the replies from the splitted messages, into a single outgoing message from the Splitter.
     * By default Camel will use the original incoming message to the splitter (leave it unchanged). You can also use a POJO as the AggregationStrategy
     */
    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }

    public Boolean getParallelProcessing() {
        return parallelProcessing;
    }

    public void setParallelProcessing(Boolean parallelProcessing) {
        this.parallelProcessing = parallelProcessing;
    }

    public Boolean getStreaming() {
        return streaming;
    }

    public void setStreaming(Boolean streaming) {
        this.streaming = streaming;
    }

    public Boolean getParallelAggregate() {
        return parallelAggregate;
    }

    public void setParallelAggregate(Boolean parallelAggregate) {
        this.parallelAggregate = parallelAggregate;
    }
    
    public Boolean getStopOnAggregateException() {
        return this.stopOnAggregateException;
    }

    public void setStopOnAggregateException(Boolean stopOnAggregateException) {
        this.stopOnAggregateException = stopOnAggregateException;
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

    /**
     * Sets a reference to the AggregationStrategy to be used to assemble the replies from the splitted messages, into a single outgoing message from the Splitter.
     * By default Camel will use the original incoming message to the splitter (leave it unchanged). You can also use a POJO as the AggregationStrategy
     */
    public void setStrategyRef(String strategyRef) {
        this.strategyRef = strategyRef;
    }

    public String getStrategyMethodName() {
        return strategyMethodName;
    }

    /**
     * This option can be used to explicit declare the method name to use, when using POJOs as the AggregationStrategy.
     */
    public void setStrategyMethodName(String strategyMethodName) {
        this.strategyMethodName = strategyMethodName;
    }

    public Boolean getStrategyMethodAllowNull() {
        return strategyMethodAllowNull;
    }

    /**
     * If this option is false then the aggregate method is not used if there was no data to enrich.
     * If this option is true then null values is used as the oldExchange (when no data to enrich), when using POJOs as the AggregationStrategy
     */
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

}
