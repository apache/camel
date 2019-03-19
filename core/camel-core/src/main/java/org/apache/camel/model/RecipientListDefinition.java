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

import java.util.concurrent.ExecutorService;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.builder.ProcessClause;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Routes messages to a number of dynamically specified recipients (dynamic to)
 */
@Metadata(label = "eip,endpoint,routing")
@XmlRootElement(name = "recipientList")
@XmlAccessorType(XmlAccessType.FIELD)
public class RecipientListDefinition<Type extends ProcessorDefinition<Type>> extends NoOutputExpressionNode implements ExecutorServiceAwareDefinition<RecipientListDefinition<Type>> {
    @XmlTransient
    private AggregationStrategy aggregationStrategy;
    @XmlTransient
    private ExecutorService executorService;
    @XmlAttribute @Metadata(defaultValue = ",")
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
    @XmlAttribute @Metadata(defaultValue = "0")
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
    @XmlAttribute
    private Boolean stopOnAggregateException;

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

    // Fluent API
    // -------------------------------------------------------------------------

    @Override
    @SuppressWarnings("unchecked")
    public Type end() {
        // allow end() to return to previous type so you can continue in the DSL
        return (Type) super.end();
    }

    /**
     * Delimiter used if the Expression returned multiple endpoints. Can be turned off using the value <tt>false</tt>.
     * <p/>
     * The default value is ,
     *
     * @param delimiter the delimiter
     * @return the builder
     */
    public RecipientListDefinition<Type> delimiter(String delimiter) {
        setDelimiter(delimiter);
        return this;
    }

    /**
     * Sets the AggregationStrategy to be used to assemble the replies from the recipients, into a single outgoing message from the RecipientList.
     * By default Camel will use the last reply as the outgoing message. You can also use a POJO as the AggregationStrategy
     */
    public RecipientListDefinition<Type> aggregationStrategy(AggregationStrategy aggregationStrategy) {
        setAggregationStrategy(aggregationStrategy);
        return this;
    }

    /**
     * Sets a reference to the AggregationStrategy to be used to assemble the replies from the recipients, into a single outgoing message from the RecipientList.
     * By default Camel will use the last reply as the outgoing message. You can also use a POJO as the AggregationStrategy
     */
    public RecipientListDefinition<Type> aggregationStrategyRef(String aggregationStrategyRef) {
        setStrategyRef(aggregationStrategyRef);
        return this;
    }

    /**
     * This option can be used to explicit declare the method name to use, when using POJOs as the AggregationStrategy.
     *
     * @param  methodName the method name to call
     * @return the builder
     */
    public RecipientListDefinition<Type> aggregationStrategyMethodName(String methodName) {
        setStrategyMethodName(methodName);
        return this;
    }

    /**
     * If this option is false then the aggregate method is not used if there was no data to enrich.
     * If this option is true then null values is used as the oldExchange (when no data to enrich), when using POJOs as the AggregationStrategy
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
     * If enabled then sending messages to the recipients occurs concurrently.
     * Note the caller thread will still wait until all messages has been fully processed, before it continues.
     * Its only the sending and processing the replies from the recipients which happens concurrently.
     *
     * @return the builder
     */
    public RecipientListDefinition<Type> parallelProcessing() {
        setParallelProcessing(true);
        return this;
    }

    /**
     * If enabled then sending messages to the recipients occurs concurrently.
     * Note the caller thread will still wait until all messages has been fully processed, before it continues.
     * Its only the sending and processing the replies from the recipients which happens concurrently.
     *
     * @return the builder
     */
    public RecipientListDefinition<Type> parallelProcessing(boolean parallelProcessing) {
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
    public RecipientListDefinition<Type> parallelAggregate() {
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
    public RecipientListDefinition<Type> stopOnAggregateException() {
        setStopOnAggregateException(true);
        return this;
    }

    /**
     * If enabled then Camel will process replies out-of-order, eg in the order they come back.
     * If disabled, Camel will process replies in the same order as defined by the recipient list.
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

    /**
     * To use a custom Thread Pool to be used for parallel processing.
     * Notice if you set this option, then parallel processing is automatic implied, and you do not have to enable that option as well.
     */
    public RecipientListDefinition<Type> executorService(ExecutorService executorService) {
        setExecutorService(executorService);
        return this;
    }

    /**
     * Refers to a custom Thread Pool to be used for parallel processing.
     * Notice if you set this option, then parallel processing is automatic implied, and you do not have to enable that option as well.
     */
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
     * Sets the {@link Processor} when preparing the {@link org.apache.camel.Exchange} to be used send using a fluent buidler.
     */
    public ProcessClause<RecipientListDefinition<Type>> onPrepare() {
        ProcessClause<RecipientListDefinition<Type>> clause = new ProcessClause<>(this);
        setOnPrepare(clause);
        return clause;
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
     * Sets a total timeout specified in millis, when using parallel processing.
     * If the Recipient List hasn't been able to send and process all replies within the given timeframe,
     * then the timeout triggers and the Recipient List breaks out and continues.
     * Notice if you provide a TimeoutAwareAggregationStrategy then the timeout method is invoked before breaking out.
     * If the timeout is reached with running tasks still remaining, certain tasks for which it is difficult for Camel
     * to shut down in a graceful manner may continue to run. So use this option with a bit of care.
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
     * Recipient List will by default not share unit of work between the parent exchange and each recipient exchange.
     * This means each sub exchange has its own individual unit of work.
     *
     * @return the builder.
     * @see org.apache.camel.spi.SubUnitOfWork
     */
    public RecipientListDefinition<Type> shareUnitOfWork() {
        setShareUnitOfWork(true);
        return this;
    }

    /**
     * Sets the maximum size used by the {@link org.apache.camel.spi.ProducerCache} which is used
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


    /**
     * Expression that returns which endpoints (url) to send the message to (the recipients).
     * If the expression return an empty value then the message is not sent to any recipients.
     */
    @Override
    public void setExpression(ExpressionDefinition expression) {
        // override to include javadoc what the expression is used for
        super.setExpression(expression);
    }

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

    public String getStrategyRef() {
        return strategyRef;
    }

    /**
     * Sets a reference to the AggregationStrategy to be used to assemble the replies from the recipients, into a single outgoing message from the RecipientList.
     * By default Camel will use the last reply as the outgoing message. You can also use a POJO as the AggregationStrategy
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

    public Boolean getIgnoreInvalidEndpoints() {
        return ignoreInvalidEndpoints;
    }

    public void setIgnoreInvalidEndpoints(Boolean ignoreInvalidEndpoints) {
        this.ignoreInvalidEndpoints = ignoreInvalidEndpoints;
    }

    public Boolean getStopOnException() {
        return stopOnException;
    }

    public void setStopOnException(Boolean stopOnException) {
        this.stopOnException = stopOnException;
    }

    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    /**
     * Sets the AggregationStrategy to be used to assemble the replies from the recipients, into a single outgoing message from the RecipientList.
     * By default Camel will use the last reply as the outgoing message. You can also use a POJO as the AggregationStrategy
     */
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

    public Integer getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(Integer cacheSize) {
        this.cacheSize = cacheSize;
    }

    public Boolean getParallelAggregate() {
        return parallelAggregate;
    }

    public void setParallelAggregate(Boolean parallelAggregate) {
        this.parallelAggregate = parallelAggregate;
    }

    public Boolean getStopOnAggregateException() {
        return stopOnAggregateException;
    }

    public void setStopOnAggregateException(Boolean stopOnAggregateException) {
        this.stopOnAggregateException = stopOnAggregateException;
    }
}
