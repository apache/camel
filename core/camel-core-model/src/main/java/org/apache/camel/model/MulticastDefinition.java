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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

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
public class MulticastDefinition extends OutputDefinition<MulticastDefinition>
        implements ExecutorServiceAwareDefinition<MulticastDefinition> {

    @XmlTransient
    private ExecutorService executorServiceBean;
    @XmlTransient
    private AggregationStrategy aggregationStrategyBean;
    @XmlTransient
    private Processor onPrepareProcessor;

    @XmlAttribute
    @Metadata(javaType = "org.apache.camel.AggregationStrategy")
    private String aggregationStrategy;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String aggregationStrategyMethodName;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String aggregationStrategyMethodAllowNull;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String parallelAggregate;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String parallelProcessing;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String synchronous;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String streaming;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String stopOnException;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.time.Duration", defaultValue = "0")
    private String timeout;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.util.concurrent.ExecutorService")
    private String executorService;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "org.apache.camel.Processor")
    private String onPrepare;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String shareUnitOfWork;

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
     * Sets the AggregationStrategy to be used to assemble the replies from the multicasts, into a single outgoing
     * message from the Multicast using a fluent builder.
     */
    public AggregationStrategyClause<MulticastDefinition> aggregationStrategy() {
        AggregationStrategyClause<MulticastDefinition> clause = new AggregationStrategyClause<>(this);
        setAggregationStrategy(clause);
        return clause;
    }

    /**
     * Sets the AggregationStrategy to be used to assemble the replies from the multicasts, into a single outgoing
     * message from the Multicast. By default Camel will use the last reply as the outgoing message. You can also use a
     * POJO as the AggregationStrategy. If an exception is thrown from the aggregate method in the AggregationStrategy,
     * then by default, that exception is not handled by the error handler. The error handler can be enabled to react if
     * enabling the shareUnitOfWork option.
     */
    public MulticastDefinition aggregationStrategy(AggregationStrategy aggregationStrategy) {
        setAggregationStrategy(aggregationStrategy);
        return this;
    }

    /**
     * Sets a reference to the AggregationStrategy to be used to assemble the replies from the multicasts, into a single
     * outgoing message from the Multicast. By default Camel will use the last reply as the outgoing message. You can
     * also use a POJO as the AggregationStrategy. If an exception is thrown from the aggregate method in the
     * AggregationStrategy, then by default, that exception is not handled by the error handler. The error handler can
     * be enabled to react if enabling the shareUnitOfWork option.
     */
    public MulticastDefinition aggregationStrategy(String aggregationStrategy) {
        setAggregationStrategy(aggregationStrategy);
        return this;
    }

    /**
     * This option can be used to explicit declare the method name to use, when using POJOs as the AggregationStrategy.
     *
     * @param  methodName the method name to call
     * @return            the builder
     */
    public MulticastDefinition aggregationStrategyMethodName(String methodName) {
        setAggregationStrategyMethodName(methodName);
        return this;
    }

    /**
     * If this option is false then the aggregate method is not used if there was no data to enrich. If this option is
     * true then null values is used as the oldExchange (when no data to enrich), when using POJOs as the
     * AggregationStrategy
     *
     * @return the builder
     */
    public MulticastDefinition aggregationStrategyMethodAllowNull() {
        setAggregationStrategyMethodAllowNull(Boolean.toString(true));
        return this;
    }

    /**
     * If enabled then sending messages to the multicasts occurs concurrently. Note the caller thread will still wait
     * until all messages has been fully processed, before it continues. Its only the sending and processing the replies
     * from the multicasts which happens concurrently.
     *
     * When parallel processing is enabled, then the Camel routing engin will continue processing using last used thread
     * from the parallel thread pool. However, if you want to use the original thread that called the multicast, then
     * make sure to enable the synchronous option as well.
     *
     * @return the builder
     */
    public MulticastDefinition parallelProcessing() {
        setParallelProcessing(Boolean.toString(true));
        return this;
    }

    /**
     * If enabled then sending messages to the multicasts occurs concurrently. Note the caller thread will still wait
     * until all messages has been fully processed, before it continues. Its only the sending and processing the replies
     * from the multicasts which happens concurrently.
     *
     * When parallel processing is enabled, then the Camel routing engin will continue processing using last used thread
     * from the parallel thread pool. However, if you want to use the original thread that called the multicast, then
     * make sure to enable the synchronous option as well.
     *
     * @return the builder
     */
    public MulticastDefinition parallelProcessing(String parallelProcessing) {
        setParallelProcessing(parallelProcessing);
        return this;
    }

    /**
     * If enabled then sending messages to the multicasts occurs concurrently. Note the caller thread will still wait
     * until all messages has been fully processed, before it continues. Its only the sending and processing the replies
     * from the multicasts which happens concurrently.
     *
     * When parallel processing is enabled, then the Camel routing engin will continue processing using last used thread
     * from the parallel thread pool. However, if you want to use the original thread that called the multicast, then
     * make sure to enable the synchronous option as well.
     *
     * @return the builder
     */
    public MulticastDefinition parallelProcessing(boolean parallelProcessing) {
        setParallelProcessing(Boolean.toString(parallelProcessing));
        return this;
    }

    /**
     * Sets whether synchronous processing should be strictly used. When enabled then the same thread is used to
     * continue routing after the multicast is complete, even if parallel processing is enabled.
     *
     * @return the builder
     */
    public MulticastDefinition synchronous() {
        return synchronous(true);
    }

    /**
     * Sets whether synchronous processing should be strictly used. When enabled then the same thread is used to
     * continue routing after the multicast is complete, even if parallel processing is enabled.
     *
     * @return the builder
     */
    public MulticastDefinition synchronous(boolean synchronous) {
        return synchronous(Boolean.toString(synchronous));
    }

    /**
     * Sets whether synchronous processing should be strictly used. When enabled then the same thread is used to
     * continue routing after the multicast is complete, even if parallel processing is enabled.
     *
     * @return the builder
     */
    public MulticastDefinition synchronous(String synchronous) {
        setSynchronous(synchronous);
        return this;
    }

    /**
     * If enabled then the aggregate method on AggregationStrategy can be called concurrently. Notice that this would
     * require the implementation of AggregationStrategy to be implemented as thread-safe. By default this is false
     * meaning that Camel synchronizes the call to the aggregate method. Though in some use-cases this can be used to
     * archive higher performance when the AggregationStrategy is implemented as thread-safe.
     *
     * @return the builder
     */
    public MulticastDefinition parallelAggregate() {
        setParallelAggregate(Boolean.toString(true));
        return this;
    }

    /**
     * If enabled then the aggregate method on AggregationStrategy can be called concurrently. Notice that this would
     * require the implementation of AggregationStrategy to be implemented as thread-safe. By default this is false
     * meaning that Camel synchronizes the call to the aggregate method. Though in some use-cases this can be used to
     * archive higher performance when the AggregationStrategy is implemented as thread-safe.
     *
     * @return the builder
     */
    public MulticastDefinition parallelAggregate(boolean parallelAggregate) {
        setParallelAggregate(Boolean.toString(parallelAggregate));
        return this;
    }

    /**
     * If enabled then the aggregate method on AggregationStrategy can be called concurrently. Notice that this would
     * require the implementation of AggregationStrategy to be implemented as thread-safe. By default this is false
     * meaning that Camel synchronizes the call to the aggregate method. Though in some use-cases this can be used to
     * archive higher performance when the AggregationStrategy is implemented as thread-safe.
     *
     * @return the builder
     */
    public MulticastDefinition parallelAggregate(String parallelAggregate) {
        setParallelAggregate(parallelAggregate);
        return this;
    }

    /**
     * If enabled then Camel will process replies out-of-order, eg in the order they come back. If disabled, Camel will
     * process replies in the same order as defined by the multicast.
     *
     * @return the builder
     */
    public MulticastDefinition streaming() {
        setStreaming(Boolean.toString(true));
        return this;
    }

    /**
     * Will now stop further processing if an exception or failure occurred during processing of an
     * {@link org.apache.camel.Exchange} and the caused exception will be thrown.
     * <p/>
     * Will also stop if processing the exchange failed (has a fault message) or an exception was thrown and handled by
     * the error handler (such as using onException). In all situations the multicast will stop further processing. This
     * is the same behavior as in pipeline, which is used by the routing engine.
     * <p/>
     * The default behavior is to <b>not</b> stop but continue processing till the end
     *
     * @return the builder
     */
    public MulticastDefinition stopOnException() {
        return stopOnException(Boolean.toString(true));
    }

    /**
     * Will now stop further processing if an exception or failure occurred during processing of an
     * {@link org.apache.camel.Exchange} and the caused exception will be thrown.
     * <p/>
     * Will also stop if processing the exchange failed (has a fault message) or an exception was thrown and handled by
     * the error handler (such as using onException). In all situations the multicast will stop further processing. This
     * is the same behavior as in pipeline, which is used by the routing engine.
     * <p/>
     * The default behavior is to <b>not</b> stop but continue processing till the end
     *
     * @return the builder
     */
    public MulticastDefinition stopOnException(String stopOnException) {
        setStopOnException(stopOnException);
        return this;
    }

    /**
     * To use a custom Thread Pool to be used for parallel processing. Notice if you set this option, then parallel
     * processing is automatically implied, and you do not have to enable that option as well.
     */
    @Override
    public MulticastDefinition executorService(ExecutorService executorService) {
        this.executorServiceBean = executorService;
        return this;
    }

    /**
     * Refers to a custom Thread Pool to be used for parallel processing. Notice if you set this option, then parallel
     * processing is automatically implied, and you do not have to enable that option as well.
     */
    @Override
    public MulticastDefinition executorService(String executorService) {
        setExecutorService(executorService);
        return this;
    }

    /**
     * Set the {@link Processor} to use when preparing the {@link org.apache.camel.Exchange} to be send using a fluent
     * builder.
     */
    public ProcessClause<MulticastDefinition> onPrepare() {
        ProcessClause<MulticastDefinition> clause = new ProcessClause<>(this);
        this.onPrepareProcessor = clause;
        return clause;
    }

    /**
     * Uses the {@link Processor} when preparing the {@link org.apache.camel.Exchange} to be send. This can be used to
     * deep-clone messages that should be send, or any custom logic needed before the exchange is send.
     *
     * @param  onPrepare the processor
     * @return           the builder
     */
    public MulticastDefinition onPrepare(Processor onPrepare) {
        this.onPrepareProcessor = onPrepare;
        return this;
    }

    /**
     * Uses the {@link Processor} when preparing the {@link org.apache.camel.Exchange} to be send. This can be used to
     * deep-clone messages that should be send, or any custom logic needed before the exchange is send.
     *
     * @param  onPrepare reference to the processor to lookup in the {@link org.apache.camel.spi.Registry}
     * @return           the builder
     */
    public MulticastDefinition onPrepare(String onPrepare) {
        setOnPrepare(onPrepare);
        return this;
    }

    /**
     * Sets a total timeout specified in millis, when using parallel processing. If the Multicast hasn't been able to
     * send and process all replies within the given timeframe, then the timeout triggers and the Multicast breaks out
     * and continues. Notice if you provide a TimeoutAwareAggregationStrategy then the timeout method is invoked before
     * breaking out. If the timeout is reached with running tasks still remaining, certain tasks for which it is
     * difficult for Camel to shut down in a graceful manner may continue to run. So use this option with a bit of care.
     *
     * @param  timeout timeout in millis
     * @return         the builder
     */
    public MulticastDefinition timeout(long timeout) {
        return timeout(Long.toString(timeout));
    }

    /**
     * Sets a total timeout specified in millis, when using parallel processing. If the Multicast hasn't been able to
     * send and process all replies within the given timeframe, then the timeout triggers and the Multicast breaks out
     * and continues. Notice if you provide a TimeoutAwareAggregationStrategy then the timeout method is invoked before
     * breaking out. If the timeout is reached with running tasks still remaining, certain tasks for which it is
     * difficult for Camel to shut down in a graceful manner may continue to run. So use this option with a bit of care.
     *
     * @param  timeout timeout in millis
     * @return         the builder
     */
    public MulticastDefinition timeout(String timeout) {
        setTimeout(timeout);
        return this;
    }

    /**
     * Shares the {@link org.apache.camel.spi.UnitOfWork} with the parent and each of the sub messages. Multicast will
     * by default not share unit of work between the parent exchange and each multicasted exchange. This means each sub
     * exchange has its own individual unit of work.
     *
     * @return the builder.
     */
    public MulticastDefinition shareUnitOfWork() {
        setShareUnitOfWork(Boolean.toString(true));
        return this;
    }

    public AggregationStrategy getAggregationStrategyBean() {
        return aggregationStrategyBean;
    }

    public Processor getOnPrepareProcessor() {
        return onPrepareProcessor;
    }

    @Override
    public ExecutorService getExecutorServiceBean() {
        return executorServiceBean;
    }

    @Override
    public String getExecutorServiceRef() {
        return executorService;
    }

    public String getParallelProcessing() {
        return parallelProcessing;
    }

    public void setParallelProcessing(String parallelProcessing) {
        this.parallelProcessing = parallelProcessing;
    }

    public String getSynchronous() {
        return synchronous;
    }

    public void setSynchronous(String synchronous) {
        this.synchronous = synchronous;
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

    public String getAggregationStrategy() {
        return aggregationStrategy;
    }

    /**
     * Refers to an AggregationStrategy to be used to assemble the replies from the multicasts, into a single outgoing
     * message from the Multicast. By default Camel will use the last reply as the outgoing message. You can also use a
     * POJO as the AggregationStrategy
     */
    public void setAggregationStrategy(String aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }

    /**
     * Refers to an AggregationStrategy to be used to assemble the replies from the multicasts, into a single outgoing
     * message from the Multicast. By default Camel will use the last reply as the outgoing message. You can also use a
     * POJO as the AggregationStrategy
     */
    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategyBean = aggregationStrategy;
    }

    public String getAggregationStrategyMethodName() {
        return aggregationStrategyMethodName;
    }

    /**
     * This option can be used to explicit declare the method name to use, when using POJOs as the AggregationStrategy.
     */
    public void setAggregationStrategyMethodName(String aggregationStrategyMethodName) {
        this.aggregationStrategyMethodName = aggregationStrategyMethodName;
    }

    public String getAggregationStrategyMethodAllowNull() {
        return aggregationStrategyMethodAllowNull;
    }

    /**
     * If this option is false then the aggregate method is not used if there was no data to enrich. If this option is
     * true then null values is used as the oldExchange (when no data to enrich), when using POJOs as the
     * AggregationStrategy
     */
    public void setAggregationStrategyMethodAllowNull(String aggregationStrategyMethodAllowNull) {
        this.aggregationStrategyMethodAllowNull = aggregationStrategyMethodAllowNull;
    }

    public String getExecutorService() {
        return executorService;
    }

    /**
     * Refers to a custom Thread Pool to be used for parallel processing. Notice if you set this option, then parallel
     * processing is automatic implied, and you do not have to enable that option as well.
     */
    public void setExecutorService(String executorService) {
        this.executorService = executorService;
    }

    public String getTimeout() {
        return timeout;
    }

    public void setTimeout(String timeout) {
        this.timeout = timeout;
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

    public String getOnPrepare() {
        return onPrepare;
    }

    public void setOnPrepare(String onPrepare) {
        this.onPrepare = onPrepare;
    }
}
