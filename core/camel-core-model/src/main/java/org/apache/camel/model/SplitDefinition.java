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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Splits a single message into many sub-messages.
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "split")
@XmlAccessorType(XmlAccessType.FIELD)
public class SplitDefinition extends OutputExpressionNode implements ExecutorServiceAwareDefinition<SplitDefinition> {

    @XmlTransient
    private ExecutorService executorServiceBean;
    @XmlTransient
    private AggregationStrategy aggregationStrategyBean;
    @XmlTransient
    private Processor onPrepareProcessor;

    @XmlAttribute
    @Metadata(defaultValue = ",")
    private String delimiter;
    @XmlAttribute
    @Metadata(javaType = "org.apache.camel.AggregationStrategy")
    private String aggregationStrategy;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String aggregationStrategyMethodName;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String aggregationStrategyMethodAllowNull;
    @Deprecated(since = "4.7.0")
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

    public SplitDefinition() {
    }

    public SplitDefinition(SplitDefinition source) {
        super(source);
        this.executorServiceBean = source.executorServiceBean;
        this.aggregationStrategyBean = source.aggregationStrategyBean;
        this.onPrepareProcessor = source.onPrepareProcessor;
        this.delimiter = source.delimiter;
        this.aggregationStrategy = source.aggregationStrategy;
        this.aggregationStrategyMethodName = source.aggregationStrategyMethodName;
        this.aggregationStrategyMethodAllowNull = source.aggregationStrategyMethodAllowNull;
        this.parallelAggregate = source.parallelAggregate;
        this.parallelProcessing = source.parallelProcessing;
        this.synchronous = source.synchronous;
        this.streaming = source.streaming;
        this.stopOnException = source.stopOnException;
        this.timeout = source.timeout;
        this.executorService = source.executorService;
        this.onPrepare = source.onPrepare;
        this.shareUnitOfWork = source.shareUnitOfWork;
    }

    public SplitDefinition(Expression expression) {
        super(expression);
    }

    public SplitDefinition(ExpressionDefinition expression) {
        super(expression);
    }

    @Override
    public SplitDefinition copyDefinition() {
        return new SplitDefinition(this);
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

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Delimiter used in splitting messages. Can be turned off using the value <tt>false</tt>. To force not splitting
     * then the delimiter can be set to <tt>single</tt> to use the value as a single list, this can be needed in some
     * special situations.
     * <p/>
     * The default value is comma.
     *
     * @param  delimiter the delimiter
     * @return           the builder
     */
    public SplitDefinition delimiter(String delimiter) {
        setDelimiter(delimiter);
        return this;
    }

    /**
     * Sets the AggregationStrategy to be used to assemble the replies from the split messages, into a single outgoing
     * message from the Splitter. By default Camel will use the original incoming message to the splitter (leave it
     * unchanged). You can also use a POJO as the AggregationStrategy
     */
    public SplitDefinition aggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategyBean = aggregationStrategy;
        return this;
    }

    /**
     * Sets a reference to the AggregationStrategy to be used to assemble the replies from the split messages, into a
     * single outgoing message from the Splitter. By default Camel will use the original incoming message to the
     * splitter (leave it unchanged). You can also use a POJO as the AggregationStrategy
     */
    public SplitDefinition aggregationStrategy(String aggregationStrategy) {
        setAggregationStrategy(aggregationStrategy);
        return this;
    }

    /**
     * This option can be used to explicit declare the method name to use, when using POJOs as the AggregationStrategy.
     *
     * @param  methodName the method name to call
     * @return            the builder
     */
    public SplitDefinition aggregationStrategyMethodName(String methodName) {
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
    public SplitDefinition aggregationStrategyMethodAllowNull() {
        return aggregationStrategyMethodAllowNull(true);
    }

    /**
     * If this option is false then the aggregate method is not used if there was no data to enrich. If this option is
     * true then null values is used as the oldExchange (when no data to enrich), when using POJOs as the
     * AggregationStrategy
     *
     * @return the builder
     */
    public SplitDefinition aggregationStrategyMethodAllowNull(boolean aggregationStrategyMethodAllowNull) {
        return aggregationStrategyMethodAllowNull(Boolean.toString(aggregationStrategyMethodAllowNull));
    }

    /**
     * If this option is false then the aggregate method is not used if there was no data to enrich. If this option is
     * true then null values is used as the oldExchange (when no data to enrich), when using POJOs as the
     * AggregationStrategy
     *
     * @return the builder
     */
    public SplitDefinition aggregationStrategyMethodAllowNull(String aggregationStrategyMethodAllowNull) {
        setAggregationStrategyMethodAllowNull(aggregationStrategyMethodAllowNull);
        return this;
    }

    /**
     * If enabled then processing each split messages occurs concurrently. Note the caller thread will still wait until
     * all messages has been fully processed, before it continues. It's only processing the sub messages from the
     * splitter which happens concurrently.
     *
     * When parallel processing is enabled, then the Camel routing engin will continue processing using last used thread
     * from the parallel thread pool. However, if you want to use the original thread that called the splitter, then
     * make sure to enable the synchronous option as well.
     *
     * In parallel processing mode, you may want to also synchronous = true to force this EIP to process the sub-tasks
     * using the upper bounds of the thread-pool. If using synchronous = false then Camel will allow its reactive
     * routing engine to use as many threads as possible, which may be available due to sub-tasks using other
     * thread-pools such as CompletableFuture.runAsync or others.
     *
     * @return the builder
     */
    public SplitDefinition parallelProcessing() {
        return parallelProcessing(true);
    }

    /**
     * If enabled then processing each split messages occurs concurrently. Note the caller thread will still wait until
     * all messages has been fully processed, before it continues. It's only processing the sub messages from the
     * splitter which happens concurrently.
     *
     * When parallel processing is enabled, then the Camel routing engin will continue processing using last used thread
     * from the parallel thread pool. However, if you want to use the original thread that called the splitter, then
     * make sure to enable the synchronous option as well.
     *
     * In parallel processing mode, you may want to also synchronous = true to force this EIP to process the sub-tasks
     * using the upper bounds of the thread-pool. If using synchronous = false then Camel will allow its reactive
     * routing engine to use as many threads as possible, which may be available due to sub-tasks using other
     * thread-pools such as CompletableFuture.runAsync or others.
     *
     * @return the builder
     */
    public SplitDefinition parallelProcessing(boolean parallelProcessing) {
        return parallelProcessing(Boolean.toString(parallelProcessing));
    }

    /**
     * If enabled then processing each split messages occurs concurrently. Note the caller thread will still wait until
     * all messages has been fully processed, before it continues. It's only processing the sub messages from the
     * splitter which happens concurrently.
     *
     * When parallel processing is enabled, then the Camel routing engin will continue processing using last used thread
     * from the parallel thread pool. However, if you want to use the original thread that called the splitter, then
     * make sure to enable the synchronous option as well.
     *
     * In parallel processing mode, you may want to also synchronous = true to force this EIP to process the sub-tasks
     * using the upper bounds of the thread-pool. If using synchronous = false then Camel will allow its reactive
     * routing engine to use as many threads as possible, which may be available due to sub-tasks using other
     * thread-pools such as CompletableFuture.runAsync or others.
     *
     * @return the builder
     */
    public SplitDefinition parallelProcessing(String parallelProcessing) {
        setParallelProcessing(parallelProcessing);
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
    @Deprecated(since = "4.7.0")
    public SplitDefinition parallelAggregate() {
        return parallelAggregate(true);
    }

    /**
     * If enabled then the aggregate method on AggregationStrategy can be called concurrently. Notice that this would
     * require the implementation of AggregationStrategy to be implemented as thread-safe. By default this is false
     * meaning that Camel synchronizes the call to the aggregate method. Though in some use-cases this can be used to
     * archive higher performance when the AggregationStrategy is implemented as thread-safe.
     *
     * @return the builder
     */
    @Deprecated(since = "4.7.0")
    public SplitDefinition parallelAggregate(boolean parallelAggregate) {
        return parallelAggregate(Boolean.toString(parallelAggregate));
    }

    /**
     * If enabled then the aggregate method on AggregationStrategy can be called concurrently. Notice that this would
     * require the implementation of AggregationStrategy to be implemented as thread-safe. By default this is false
     * meaning that Camel synchronizes the call to the aggregate method. Though in some use-cases this can be used to
     * archive higher performance when the AggregationStrategy is implemented as thread-safe.
     *
     * @return the builder
     */
    @Deprecated(since = "4.7.0")
    public SplitDefinition parallelAggregate(String parallelAggregate) {
        setParallelAggregate(parallelAggregate);
        return this;
    }

    /**
     * Sets whether synchronous processing should be strictly used. When enabled then the same thread is used to
     * continue routing after the split is complete, even if parallel processing is enabled.
     *
     * @return the builder
     */
    public SplitDefinition synchronous() {
        return synchronous(true);
    }

    /**
     * Sets whether synchronous processing should be strictly used. When enabled then the same thread is used to
     * continue routing after the split is complete, even if parallel processing is enabled.
     *
     * @return the builder
     */
    public SplitDefinition synchronous(boolean synchronous) {
        return synchronous(Boolean.toString(synchronous));
    }

    /**
     * Sets whether synchronous processing should be strictly used. When enabled then the same thread is used to
     * continue routing after the split is complete, even if parallel processing is enabled.
     *
     * @return the builder
     */
    public SplitDefinition synchronous(String synchronous) {
        setSynchronous(synchronous);
        return this;
    }

    /**
     * When in streaming mode, then the splitter splits the original message on-demand, and each split message is
     * processed one by one. This reduces memory usage as the splitter do not split all the messages first, but then we
     * do not know the total size, and therefore the {@link org.apache.camel.Exchange#SPLIT_SIZE} is empty.
     * <p/>
     * In non-streaming mode (default) the splitter will split each message first, to know the total size, and then
     * process each message one by one. This requires to keep all the split messages in memory and therefore requires
     * more memory. The total size is provided in the {@link org.apache.camel.Exchange#SPLIT_SIZE} header.
     * <p/>
     * The streaming mode also affects the aggregation behavior. If enabled then Camel will process replies
     * out-of-order, e.g. in the order they come back. If disabled, Camel will process replies in the same order as the
     * messages was split.
     *
     * @return the builder
     */
    public SplitDefinition streaming() {
        return streaming(true);
    }

    /**
     * When in streaming mode, then the splitter splits the original message on-demand, and each split message is
     * processed one by one. This reduces memory usage as the splitter do not split all the messages first, but then we
     * do not know the total size, and therefore the {@link org.apache.camel.Exchange#SPLIT_SIZE} is empty.
     * <p/>
     * In non-streaming mode (default) the splitter will split each message first, to know the total size, and then
     * process each message one by one. This requires to keep all the split messages in memory and therefore requires
     * more memory. The total size is provided in the {@link org.apache.camel.Exchange#SPLIT_SIZE} header.
     * <p/>
     * The streaming mode also affects the aggregation behavior. If enabled then Camel will process replies
     * out-of-order, e.g. in the order they come back. If disabled, Camel will process replies in the same order as the
     * messages was split.
     *
     * @return the builder
     */
    public SplitDefinition streaming(boolean streaming) {
        return streaming(Boolean.toString(streaming));
    }

    /**
     * When in streaming mode, then the splitter splits the original message on-demand, and each split message is
     * processed one by one. This reduces memory usage as the splitter do not split all the messages first, but then we
     * do not know the total size, and therefore the {@link org.apache.camel.Exchange#SPLIT_SIZE} is empty.
     * <p/>
     * In non-streaming mode (default) the splitter will split each message first, to know the total size, and then
     * process each message one by one. This requires to keep all the split messages in memory and therefore requires
     * more memory. The total size is provided in the {@link org.apache.camel.Exchange#SPLIT_SIZE} header.
     * <p/>
     * The streaming mode also affects the aggregation behavior. If enabled then Camel will process replies
     * out-of-order, e.g. in the order they come back. If disabled, Camel will process replies in the same order as the
     * messages was split.
     *
     * @return the builder
     */
    public SplitDefinition streaming(String streaming) {
        setStreaming(streaming);
        return this;
    }

    /**
     * Will now stop further processing if an exception or failure occurred during processing of an
     * {@link org.apache.camel.Exchange} and the caused exception will be thrown.
     * <p/>
     * Will also stop if processing the exchange failed (has a fault message) or an exception was thrown and handled by
     * the error handler (such as using onException). In all situations the splitter will stop further processing. This
     * is the same behavior as in pipeline, which is used by the routing engine.
     * <p/>
     * The default behavior is to <b>not</b> stop but continue processing till the end
     *
     * @return the builder
     */
    public SplitDefinition stopOnException() {
        return stopOnException(true);
    }

    /**
     * Will now stop further processing if an exception or failure occurred during processing of an
     * {@link org.apache.camel.Exchange} and the caused exception will be thrown.
     * <p/>
     * Will also stop if processing the exchange failed (has a fault message) or an exception was thrown and handled by
     * the error handler (such as using onException). In all situations the splitter will stop further processing. This
     * is the same behavior as in pipeline, which is used by the routing engine.
     * <p/>
     * The default behavior is to <b>not</b> stop but continue processing till the end
     *
     * @return the builder
     */
    public SplitDefinition stopOnException(boolean stopOnException) {
        return stopOnException(Boolean.toString(stopOnException));
    }

    /**
     * Will now stop further processing if an exception or failure occurred during processing of an
     * {@link org.apache.camel.Exchange} and the caused exception will be thrown.
     * <p/>
     * Will also stop if processing the exchange failed (has a fault message) or an exception was thrown and handled by
     * the error handler (such as using onException). In all situations the splitter will stop further processing. This
     * is the same behavior as in pipeline, which is used by the routing engine.
     * <p/>
     * The default behavior is to <b>not</b> stop but continue processing till the end
     *
     * @return the builder
     */
    public SplitDefinition stopOnException(String stopOnException) {
        setStopOnException(stopOnException);
        return this;
    }

    /**
     * To use a custom Thread Pool to be used for parallel processing. Notice if you set this option, then parallel
     * processing is automatically implied, and you do not have to enable that option as well.
     */
    @Override
    public SplitDefinition executorService(ExecutorService executorService) {
        this.executorServiceBean = executorService;
        return this;
    }

    /**
     * Refers to a custom Thread Pool to be used for parallel processing. Notice if you set this option, then parallel
     * processing is automatically implied, and you do not have to enable that option as well.
     */
    @Override
    public SplitDefinition executorService(String executorService) {
        setExecutorService(executorService);
        return this;
    }

    /**
     * Uses the {@link Processor} when preparing the {@link org.apache.camel.Exchange} to be sent. This can be used to
     * deep-clone messages that should be sent, or any custom logic needed before the exchange is sent.
     *
     * @param  onPrepare the processor
     * @return           the builder
     */
    public SplitDefinition onPrepare(Processor onPrepare) {
        this.onPrepareProcessor = onPrepare;
        return this;
    }

    /**
     * Uses the {@link Processor} when preparing the {@link org.apache.camel.Exchange} to be sent. This can be used to
     * deep-clone messages that should be sent, or any custom logic needed before the exchange is sent.
     *
     * @param  onPrepare reference to the processor to lookup in the {@link org.apache.camel.spi.Registry}
     * @return           the builder
     */
    public SplitDefinition onPrepare(String onPrepare) {
        setOnPrepare(onPrepare);
        return this;
    }

    /**
     * Sets a total timeout specified in millis, when using parallel processing. If the Splitter hasn't been able to
     * send and process all replies within the given timeframe, then the timeout triggers and the Splitter breaks out
     * and continues. The timeout method is invoked before breaking out. If the timeout is reached with running tasks
     * still remaining, certain tasks for which it is difficult for Camel to shut down in a graceful manner may continue
     * to run. So use this option with a bit of care.
     *
     * @param  timeout timeout in millis
     * @return         the builder
     */
    public SplitDefinition timeout(long timeout) {
        return timeout(Long.toString(timeout));
    }

    /**
     * Sets a total timeout specified in millis, when using parallel processing. If the Splitter hasn't been able to
     * send and process all replies within the given timeframe, then the timeout triggers and the Splitter breaks out
     * and continues. The timeout method is invoked before breaking out. If the timeout is reached with running tasks
     * still remaining, certain tasks for which it is difficult for Camel to shut down in a graceful manner may continue
     * to run. So use this option with a bit of care.
     *
     * @param  timeout timeout in millis
     * @return         the builder
     */
    public SplitDefinition timeout(String timeout) {
        setTimeout(timeout);
        return this;
    }

    /**
     * Shares the {@link org.apache.camel.spi.UnitOfWork} with the parent and each of the sub messages. Splitter will by
     * default not share unit of work between the parent exchange and each split exchange. This means each split
     * exchange has its own individual unit of work.
     *
     * @return the builder.
     */
    public SplitDefinition shareUnitOfWork() {
        return shareUnitOfWork(true);
    }

    /**
     * Shares the {@link org.apache.camel.spi.UnitOfWork} with the parent and each of the sub messages. Splitter will by
     * default not share unit of work between the parent exchange and each split exchange. This means each split
     * exchange has its own individual unit of work.
     *
     * @return the builder.
     */
    public SplitDefinition shareUnitOfWork(boolean shareUnitOfWork) {
        return shareUnitOfWork(Boolean.toString(shareUnitOfWork));
    }

    /**
     * Shares the {@link org.apache.camel.spi.UnitOfWork} with the parent and each of the sub messages. Splitter will by
     * default not share unit of work between the parent exchange and each split exchange. This means each split
     * exchange has its own individual unit of work.
     *
     * @return the builder.
     */
    public SplitDefinition shareUnitOfWork(String shareUnitOfWork) {
        setShareUnitOfWork(shareUnitOfWork);
        return this;
    }

    // Properties
    // -------------------------------------------------------------------------

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

    /**
     * Expression of how to split the message body, such as as-is, using a tokenizer, or using a xpath.
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

    @Deprecated(since = "4.7.0")
    public String getParallelAggregate() {
        return parallelAggregate;
    }

    @Deprecated(since = "4.7.0")
    public void setParallelAggregate(String parallelAggregate) {
        this.parallelAggregate = parallelAggregate;
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
     * Sets a reference to the AggregationStrategy to be used to assemble the replies from the split messages, into a
     * single outgoing message from the Splitter. By default Camel will use the original incoming message to the
     * splitter (leave it unchanged). You can also use a POJO as the AggregationStrategy
     */
    public void setAggregationStrategy(String aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }

    /**
     * Sets the AggregationStrategy to be used to assemble the replies from the split messages, into a single outgoing
     * message from the Splitter. By default Camel will use the original incoming message to the splitter (leave it
     * unchanged). You can also use a POJO as the AggregationStrategy
     */
    public void setAggregationStrategy(AggregationStrategy aggregationStrategyBean) {
        this.aggregationStrategyBean = aggregationStrategyBean;
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

    public String getTimeout() {
        return timeout;
    }

    public void setTimeout(String timeout) {
        this.timeout = timeout;
    }

    public String getOnPrepare() {
        return onPrepare;
    }

    public void setOnPrepare(String onPrepare) {
        this.onPrepare = onPrepare;
    }

    public String getShareUnitOfWork() {
        return shareUnitOfWork;
    }

    public void setShareUnitOfWork(String shareUnitOfWork) {
        this.shareUnitOfWork = shareUnitOfWork;
    }

    public String getExecutorService() {
        return executorService;
    }

    public void setExecutorService(String executorService) {
        this.executorService = executorService;
    }
}
