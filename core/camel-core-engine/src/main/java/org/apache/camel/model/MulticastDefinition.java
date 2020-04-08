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

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Processor;
import org.apache.camel.builder.AggregationStrategyClause;
import org.apache.camel.builder.ProcessClause;
import org.apache.camel.spi.Metadata;

/**
 * Routes the same message to multiple paths either sequentially or in parallel.
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "multicast")
@XmlAccessorType(XmlAccessType.FIELD)
public class MulticastDefinition extends OutputDefinition<MulticastDefinition> implements ExecutorServiceAwareDefinition<MulticastDefinition> {
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String parallelProcessing;
    @XmlAttribute
    private String strategyRef;
    @XmlAttribute
    private String strategyMethodName;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String strategyMethodAllowNull;
    @XmlTransient
    private ExecutorService executorService;
    @XmlAttribute
    private String executorServiceRef;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String streaming;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String stopOnException;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Long", defaultValue = "0")
    private String timeout;
    @XmlTransient
    private AggregationStrategy aggregationStrategy;
    @XmlAttribute
    private String onPrepareRef;
    @XmlTransient
    private Processor onPrepare;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String shareUnitOfWork;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String parallelAggregate;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String stopOnAggregateException;

    public MulticastDefinition() {
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

    @Override
    public String toString() {
        return "Multicast[" + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "multicast";
    }

    @Override
    public String getLabel() {
        return "multicast";
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Sets the AggregationStrategy to be used to assemble the replies from the
     * multicasts, into a single outgoing message from the Multicast using a
     * fluent builder.
     */
    public AggregationStrategyClause<MulticastDefinition> aggregationStrategy() {
        AggregationStrategyClause<MulticastDefinition> clause = new AggregationStrategyClause<>(this);
        setAggregationStrategy(clause);
        return clause;
    }

    /**
     * Sets the AggregationStrategy to be used to assemble the replies from the
     * multicasts, into a single outgoing message from the Multicast. By default
     * Camel will use the last reply as the outgoing message. You can also use a
     * POJO as the AggregationStrategy. If an exception is thrown from the
     * aggregate method in the AggregationStrategy, then by default, that
     * exception is not handled by the error handler. The error handler can be
     * enabled to react if enabling the shareUnitOfWork option.
     */
    public MulticastDefinition aggregationStrategy(AggregationStrategy aggregationStrategy) {
        setAggregationStrategy(aggregationStrategy);
        return this;
    }

    /**
     * Sets the AggregationStrategy to be used to assemble the replies from the
     * multicasts, into a single outgoing message from the Multicast. By default
     * Camel will use the last reply as the outgoing message. You can also use a
     * POJO as the AggregationStrategy. If an exception is thrown from the
     * aggregate method in the AggregationStrategy, then by default, that
     * exception is not handled by the error handler. The error handler can be
     * enabled to react if enabling the shareUnitOfWork option.
     */
    public MulticastDefinition aggregationStrategy(Supplier<AggregationStrategy> aggregationStrategy) {
        setAggregationStrategy(aggregationStrategy.get());
        return this;
    }

    /**
     * Sets a reference to the AggregationStrategy to be used to assemble the
     * replies from the multicasts, into a single outgoing message from the
     * Multicast. By default Camel will use the last reply as the outgoing
     * message. You can also use a POJO as the AggregationStrategy If an
     * exception is thrown from the aggregate method in the AggregationStrategy,
     * then by default, that exception is not handled by the error handler. The
     * error handler can be enabled to react if enabling the shareUnitOfWork
     * option.
     */
    public MulticastDefinition aggregationStrategyRef(String aggregationStrategyRef) {
        setStrategyRef(aggregationStrategyRef);
        return this;
    }

    /**
     * This option can be used to explicit declare the method name to use, when
     * using POJOs as the AggregationStrategy.
     *
     * @param methodName the method name to call
     * @return the builder
     */
    public MulticastDefinition aggregationStrategyMethodName(String methodName) {
        setStrategyMethodName(methodName);
        return this;
    }

    /**
     * If this option is false then the aggregate method is not used if there
     * was no data to enrich. If this option is true then null values is used as
     * the oldExchange (when no data to enrich), when using POJOs as the
     * AggregationStrategy
     *
     * @return the builder
     */
    public MulticastDefinition aggregationStrategyMethodAllowNull() {
        setStrategyMethodAllowNull(Boolean.toString(true));
        return this;
    }

    /**
     * If enabled then sending messages to the multicasts occurs concurrently.
     * Note the caller thread will still wait until all messages has been fully
     * processed, before it continues. Its only the sending and processing the
     * replies from the multicasts which happens concurrently.
     *
     * @return the builder
     */
    public MulticastDefinition parallelProcessing() {
        setParallelProcessing(Boolean.toString(true));
        return this;
    }

    /**
     * If enabled then sending messages to the multicasts occurs concurrently.
     * Note the caller thread will still wait until all messages has been fully
     * processed, before it continues. Its only the sending and processing the
     * replies from the multicasts which happens concurrently.
     *
     * @return the builder
     */
    public MulticastDefinition parallelProcessing(boolean parallelProcessing) {
        setParallelProcessing(Boolean.toString(parallelProcessing));
        return this;
    }

    /**
     * If enabled then the aggregate method on AggregationStrategy can be called
     * concurrently. Notice that this would require the implementation of
     * AggregationStrategy to be implemented as thread-safe. By default this is
     * false meaning that Camel synchronizes the call to the aggregate method.
     * Though in some use-cases this can be used to archive higher performance
     * when the AggregationStrategy is implemented as thread-safe.
     *
     * @return the builder
     */
    public MulticastDefinition parallelAggregate() {
        setParallelAggregate(Boolean.toString(true));
        return this;
    }

    /**
     * If enabled, unwind exceptions occurring at aggregation time to the error
     * handler when parallelProcessing is used. Currently, aggregation time
     * exceptions do not stop the route processing when parallelProcessing is
     * used. Enabling this option allows to work around this behavior. The
     * default value is <code>false</code> for the sake of backward
     * compatibility.
     *
     * @return the builder
     */
    public MulticastDefinition stopOnAggregateException() {
        setStopOnAggregateException(Boolean.toString(true));
        return this;
    }

    /**
     * If enabled then Camel will process replies out-of-order, eg in the order
     * they come back. If disabled, Camel will process replies in the same order
     * as defined by the multicast.
     *
     * @return the builder
     */
    public MulticastDefinition streaming() {
        setStreaming(Boolean.toString(true));
        return this;
    }

    /**
     * Will now stop further processing if an exception or failure occurred
     * during processing of an {@link org.apache.camel.Exchange} and the caused
     * exception will be thrown.
     * <p/>
     * Will also stop if processing the exchange failed (has a fault message) or
     * an exception was thrown and handled by the error handler (such as using
     * onException). In all situations the multicast will stop further
     * processing. This is the same behavior as in pipeline, which is used by
     * the routing engine.
     * <p/>
     * The default behavior is to <b>not</b> stop but continue processing till
     * the end
     *
     * @return the builder
     */
    public MulticastDefinition stopOnException() {
        return stopOnException(Boolean.toString(true));
    }

    /**
     * Will now stop further processing if an exception or failure occurred
     * during processing of an {@link org.apache.camel.Exchange} and the caused
     * exception will be thrown.
     * <p/>
     * Will also stop if processing the exchange failed (has a fault message) or
     * an exception was thrown and handled by the error handler (such as using
     * onException). In all situations the multicast will stop further
     * processing. This is the same behavior as in pipeline, which is used by
     * the routing engine.
     * <p/>
     * The default behavior is to <b>not</b> stop but continue processing till
     * the end
     *
     * @return the builder
     */
    public MulticastDefinition stopOnException(String stopOnException) {
        setStopOnException(stopOnException);
        return this;
    }

    /**
     * To use a custom Thread Pool to be used for parallel processing. Notice if
     * you set this option, then parallel processing is automatic implied, and
     * you do not have to enable that option as well.
     */
    @Override
    public MulticastDefinition executorService(ExecutorService executorService) {
        setExecutorService(executorService);
        return this;
    }

    /**
     * Refers to a custom Thread Pool to be used for parallel processing. Notice
     * if you set this option, then parallel processing is automatic implied,
     * and you do not have to enable that option as well.
     */
    @Override
    public MulticastDefinition executorServiceRef(String executorServiceRef) {
        setExecutorServiceRef(executorServiceRef);
        return this;
    }

    /**
     * Set the {@link Processor} to use when preparing the
     * {@link org.apache.camel.Exchange} to be send using a fluent builder.
     */
    public ProcessClause<MulticastDefinition> onPrepare() {
        ProcessClause<MulticastDefinition> clause = new ProcessClause<>(this);
        setOnPrepare(clause);
        return clause;
    }

    /**
     * Uses the {@link Processor} when preparing the
     * {@link org.apache.camel.Exchange} to be send. This can be used to
     * deep-clone messages that should be send, or any custom logic needed
     * before the exchange is send.
     *
     * @param onPrepare the processor
     * @return the builder
     */
    public MulticastDefinition onPrepare(Processor onPrepare) {
        setOnPrepare(onPrepare);
        return this;
    }

    /**
     * Uses the {@link Processor} when preparing the
     * {@link org.apache.camel.Exchange} to be send. This can be used to
     * deep-clone messages that should be send, or any custom logic needed
     * before the exchange is send.
     *
     * @param onPrepareRef reference to the processor to lookup in the
     *            {@link org.apache.camel.spi.Registry}
     * @return the builder
     */
    public MulticastDefinition onPrepareRef(String onPrepareRef) {
        setOnPrepareRef(onPrepareRef);
        return this;
    }

    /**
     * Sets a total timeout specified in millis, when using parallel processing.
     * If the Multicast hasn't been able to send and process all replies within
     * the given timeframe, then the timeout triggers and the Multicast breaks
     * out and continues. Notice if you provide a
     * TimeoutAwareAggregationStrategy then the timeout method is invoked before
     * breaking out. If the timeout is reached with running tasks still
     * remaining, certain tasks for which it is difficult for Camel to shut down
     * in a graceful manner may continue to run. So use this option with a bit
     * of care.
     *
     * @param timeout timeout in millis
     * @return the builder
     */
    public MulticastDefinition timeout(long timeout) {
        return timeout(Long.toString(timeout));
    }

    /**
     * Sets a total timeout specified in millis, when using parallel processing.
     * If the Multicast hasn't been able to send and process all replies within
     * the given timeframe, then the timeout triggers and the Multicast breaks
     * out and continues. Notice if you provide a
     * TimeoutAwareAggregationStrategy then the timeout method is invoked before
     * breaking out. If the timeout is reached with running tasks still
     * remaining, certain tasks for which it is difficult for Camel to shut down
     * in a graceful manner may continue to run. So use this option with a bit
     * of care.
     *
     * @param timeout timeout in millis
     * @return the builder
     */
    public MulticastDefinition timeout(String timeout) {
        setTimeout(timeout);
        return this;
    }

    /**
     * Shares the {@link org.apache.camel.spi.UnitOfWork} with the parent and
     * each of the sub messages. Multicast will by default not share unit of
     * work between the parent exchange and each multicasted exchange. This
     * means each sub exchange has its own individual unit of work.
     *
     * @return the builder.
     */
    public MulticastDefinition shareUnitOfWork() {
        setShareUnitOfWork(Boolean.toString(true));
        return this;
    }

    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    public MulticastDefinition setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
        return this;
    }

    public String getParallelProcessing() {
        return parallelProcessing;
    }

    public void setParallelProcessing(String parallelProcessing) {
        this.parallelProcessing = parallelProcessing;
    }

    public String getStreaming() {
        return streaming;
    }

    public void setStreaming(String streaming) {
        this.streaming = streaming;
    }

    public String getStopOnException() {
        return stopOnException;
    }

    public void setStopOnException(String stopOnException) {
        this.stopOnException = stopOnException;
    }

    @Override
    public ExecutorService getExecutorService() {
        return executorService;
    }

    @Override
    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public String getStrategyRef() {
        return strategyRef;
    }

    /**
     * Refers to an AggregationStrategy to be used to assemble the replies from
     * the multicasts, into a single outgoing message from the Multicast. By
     * default Camel will use the last reply as the outgoing message. You can
     * also use a POJO as the AggregationStrategy
     */
    public void setStrategyRef(String strategyRef) {
        this.strategyRef = strategyRef;
    }

    public String getStrategyMethodName() {
        return strategyMethodName;
    }

    /**
     * This option can be used to explicit declare the method name to use, when
     * using POJOs as the AggregationStrategy.
     */
    public void setStrategyMethodName(String strategyMethodName) {
        this.strategyMethodName = strategyMethodName;
    }

    public String getStrategyMethodAllowNull() {
        return strategyMethodAllowNull;
    }

    /**
     * If this option is false then the aggregate method is not used if there
     * was no data to enrich. If this option is true then null values is used as
     * the oldExchange (when no data to enrich), when using POJOs as the
     * AggregationStrategy
     */
    public void setStrategyMethodAllowNull(String strategyMethodAllowNull) {
        this.strategyMethodAllowNull = strategyMethodAllowNull;
    }

    @Override
    public String getExecutorServiceRef() {
        return executorServiceRef;
    }

    /**
     * Refers to a custom Thread Pool to be used for parallel processing. Notice
     * if you set this option, then parallel processing is automatic implied,
     * and you do not have to enable that option as well.
     */
    @Override
    public void setExecutorServiceRef(String executorServiceRef) {
        this.executorServiceRef = executorServiceRef;
    }

    public String getTimeout() {
        return timeout;
    }

    public void setTimeout(String timeout) {
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

    public String getShareUnitOfWork() {
        return shareUnitOfWork;
    }

    public void setShareUnitOfWork(String shareUnitOfWork) {
        this.shareUnitOfWork = shareUnitOfWork;
    }

    public String getParallelAggregate() {
        return parallelAggregate;
    }

    public void setParallelAggregate(String parallelAggregate) {
        this.parallelAggregate = parallelAggregate;
    }

    public String getStopOnAggregateException() {
        return stopOnAggregateException;
    }

    public void setStopOnAggregateException(String stopOnAggregateException) {
        this.stopOnAggregateException = stopOnAggregateException;
    }

}
