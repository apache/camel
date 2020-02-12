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
import java.util.function.Supplier;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

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
    private AggregationStrategy aggregationStrategy;
    @XmlTransient
    private ExecutorService executorService;
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
    @XmlAttribute
    private String executorServiceRef;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String streaming;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String stopOnException;
    @XmlAttribute
    @Metadata(defaultValue = "0", javaType = "java.lang.Long")
    private String timeout;
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

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Sets the AggregationStrategy to be used to assemble the replies from the
     * splitted messages, into a single outgoing message from the Splitter. By
     * default Camel will use the original incoming message to the splitter
     * (leave it unchanged). You can also use a POJO as the AggregationStrategy
     */
    public SplitDefinition aggregationStrategy(AggregationStrategy aggregationStrategy) {
        setAggregationStrategy(aggregationStrategy);
        return this;
    }

    /**
     * Sets the AggregationStrategy to be used to assemble the replies from the
     * splitted messages, into a single outgoing message from the Splitter. By
     * default Camel will use the original incoming message to the splitter
     * (leave it unchanged). You can also use a POJO as the AggregationStrategy
     */
    public SplitDefinition aggregationStrategy(Supplier<AggregationStrategy> aggregationStrategy) {
        setAggregationStrategy(aggregationStrategy.get());
        return this;
    }

    /**
     * Sets a reference to the AggregationStrategy to be used to assemble the
     * replies from the splitted messages, into a single outgoing message from
     * the Splitter. By default Camel will use the original incoming message to
     * the splitter (leave it unchanged). You can also use a POJO as the
     * AggregationStrategy
     */
    public SplitDefinition aggregationStrategyRef(String aggregationStrategyRef) {
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
    public SplitDefinition aggregationStrategyMethodName(String methodName) {
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
    public SplitDefinition aggregationStrategyMethodAllowNull() {
        return aggregationStrategyMethodAllowNull(true);
    }

    /**
     * If this option is false then the aggregate method is not used if there
     * was no data to enrich. If this option is true then null values is used as
     * the oldExchange (when no data to enrich), when using POJOs as the
     * AggregationStrategy
     *
     * @return the builder
     */
    public SplitDefinition aggregationStrategyMethodAllowNull(boolean aggregationStrategyMethodAllowNull) {
        return aggregationStrategyMethodAllowNull(Boolean.toString(aggregationStrategyMethodAllowNull));
    }

    /**
     * If this option is false then the aggregate method is not used if there
     * was no data to enrich. If this option is true then null values is used as
     * the oldExchange (when no data to enrich), when using POJOs as the
     * AggregationStrategy
     *
     * @return the builder
     */
    public SplitDefinition aggregationStrategyMethodAllowNull(String aggregationStrategyMethodAllowNull) {
        setStrategyMethodAllowNull(aggregationStrategyMethodAllowNull);
        return this;
    }

    /**
     * If enabled then processing each splitted messages occurs concurrently.
     * Note the caller thread will still wait until all messages has been fully
     * processed, before it continues. Its only processing the sub messages from
     * the splitter which happens concurrently.
     *
     * @return the builder
     */
    public SplitDefinition parallelProcessing() {
        return parallelProcessing(true);
    }

    /**
     * If enabled then processing each splitted messages occurs concurrently.
     * Note the caller thread will still wait until all messages has been fully
     * processed, before it continues. Its only processing the sub messages from
     * the splitter which happens concurrently.
     *
     * @return the builder
     */
    public SplitDefinition parallelProcessing(boolean parallelProcessing) {
        return parallelProcessing(Boolean.toString(parallelProcessing));
    }

    /**
     * If enabled then processing each splitted messages occurs concurrently.
     * Note the caller thread will still wait until all messages has been fully
     * processed, before it continues. Its only processing the sub messages from
     * the splitter which happens concurrently.
     *
     * @return the builder
     */
    public SplitDefinition parallelProcessing(String parallelProcessing) {
        setParallelProcessing(parallelProcessing);
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
    public SplitDefinition parallelAggregate() {
        return parallelAggregate(true);
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
    public SplitDefinition parallelAggregate(boolean parallelAggregate) {
        return parallelAggregate(Boolean.toString(parallelAggregate));
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
    public SplitDefinition parallelAggregate(String parallelAggregate) {
        setParallelAggregate(parallelAggregate);
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
    public SplitDefinition stopOnAggregateException() {
        return stopOnAggregateException(true);
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
    public SplitDefinition stopOnAggregateException(boolean stopOnAggregateException) {
        return stopOnAggregateException(Boolean.toString(stopOnAggregateException));
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
    public SplitDefinition stopOnAggregateException(String stopOnAggregateException) {
        setStopOnAggregateException(stopOnAggregateException);
        return this;
    }

    /**
     * When in streaming mode, then the splitter splits the original message
     * on-demand, and each splitted message is processed one by one. This
     * reduces memory usage as the splitter do not split all the messages first,
     * but then we do not know the total size, and therefore the
     * {@link org.apache.camel.Exchange#SPLIT_SIZE} is empty.
     * <p/>
     * In non-streaming mode (default) the splitter will split each message
     * first, to know the total size, and then process each message one by one.
     * This requires to keep all the splitted messages in memory and therefore
     * requires more memory. The total size is provided in the
     * {@link org.apache.camel.Exchange#SPLIT_SIZE} header.
     * <p/>
     * The streaming mode also affects the aggregation behavior. If enabled then
     * Camel will process replies out-of-order, eg in the order they come back.
     * If disabled, Camel will process replies in the same order as the messages
     * was splitted.
     *
     * @return the builder
     */
    public SplitDefinition streaming() {
        return streaming(true);
    }

    /**
     * When in streaming mode, then the splitter splits the original message
     * on-demand, and each splitted message is processed one by one. This
     * reduces memory usage as the splitter do not split all the messages first,
     * but then we do not know the total size, and therefore the
     * {@link org.apache.camel.Exchange#SPLIT_SIZE} is empty.
     * <p/>
     * In non-streaming mode (default) the splitter will split each message
     * first, to know the total size, and then process each message one by one.
     * This requires to keep all the splitted messages in memory and therefore
     * requires more memory. The total size is provided in the
     * {@link org.apache.camel.Exchange#SPLIT_SIZE} header.
     * <p/>
     * The streaming mode also affects the aggregation behavior. If enabled then
     * Camel will process replies out-of-order, eg in the order they come back.
     * If disabled, Camel will process replies in the same order as the messages
     * was splitted.
     *
     * @return the builder
     */
    public SplitDefinition streaming(boolean streaming) {
        return streaming(Boolean.toString(streaming));
    }

    /**
     * When in streaming mode, then the splitter splits the original message
     * on-demand, and each splitted message is processed one by one. This
     * reduces memory usage as the splitter do not split all the messages first,
     * but then we do not know the total size, and therefore the
     * {@link org.apache.camel.Exchange#SPLIT_SIZE} is empty.
     * <p/>
     * In non-streaming mode (default) the splitter will split each message
     * first, to know the total size, and then process each message one by one.
     * This requires to keep all the splitted messages in memory and therefore
     * requires more memory. The total size is provided in the
     * {@link org.apache.camel.Exchange#SPLIT_SIZE} header.
     * <p/>
     * The streaming mode also affects the aggregation behavior. If enabled then
     * Camel will process replies out-of-order, eg in the order they come back.
     * If disabled, Camel will process replies in the same order as the messages
     * was splitted.
     *
     * @return the builder
     */
    public SplitDefinition streaming(String streaming) {
        setStreaming(streaming);
        return this;
    }

    /**
     * Will now stop further processing if an exception or failure occurred
     * during processing of an {@link org.apache.camel.Exchange} and the caused
     * exception will be thrown.
     * <p/>
     * Will also stop if processing the exchange failed (has a fault message) or
     * an exception was thrown and handled by the error handler (such as using
     * onException). In all situations the splitter will stop further
     * processing. This is the same behavior as in pipeline, which is used by
     * the routing engine.
     * <p/>
     * The default behavior is to <b>not</b> stop but continue processing till
     * the end
     *
     * @return the builder
     */
    public SplitDefinition stopOnException() {
        return stopOnException(true);
    }

    /**
     * Will now stop further processing if an exception or failure occurred
     * during processing of an {@link org.apache.camel.Exchange} and the caused
     * exception will be thrown.
     * <p/>
     * Will also stop if processing the exchange failed (has a fault message) or
     * an exception was thrown and handled by the error handler (such as using
     * onException). In all situations the splitter will stop further
     * processing. This is the same behavior as in pipeline, which is used by
     * the routing engine.
     * <p/>
     * The default behavior is to <b>not</b> stop but continue processing till
     * the end
     *
     * @return the builder
     */
    public SplitDefinition stopOnException(boolean stopOnException) {
        return stopOnException(Boolean.toString(stopOnException));
    }

    /**
     * Will now stop further processing if an exception or failure occurred
     * during processing of an {@link org.apache.camel.Exchange} and the caused
     * exception will be thrown.
     * <p/>
     * Will also stop if processing the exchange failed (has a fault message) or
     * an exception was thrown and handled by the error handler (such as using
     * onException). In all situations the splitter will stop further
     * processing. This is the same behavior as in pipeline, which is used by
     * the routing engine.
     * <p/>
     * The default behavior is to <b>not</b> stop but continue processing till
     * the end
     *
     * @return the builder
     */
    public SplitDefinition stopOnException(String stopOnException) {
        setStopOnException(stopOnException);
        return this;
    }

    /**
     * To use a custom Thread Pool to be used for parallel processing. Notice if
     * you set this option, then parallel processing is automatic implied, and
     * you do not have to enable that option as well.
     */
    @Override
    public SplitDefinition executorService(ExecutorService executorService) {
        setExecutorService(executorService);
        return this;
    }

    /**
     * Refers to a custom Thread Pool to be used for parallel processing. Notice
     * if you set this option, then parallel processing is automatic implied,
     * and you do not have to enable that option as well.
     */
    @Override
    public SplitDefinition executorServiceRef(String executorServiceRef) {
        setExecutorServiceRef(executorServiceRef);
        return this;
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
    public SplitDefinition onPrepare(Processor onPrepare) {
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
    public SplitDefinition onPrepareRef(String onPrepareRef) {
        setOnPrepareRef(onPrepareRef);
        return this;
    }

    /**
     * Sets a total timeout specified in millis, when using parallel processing.
     * If the Splitter hasn't been able to split and process all the sub
     * messages within the given timeframe, then the timeout triggers and the
     * Splitter breaks out and continues. Notice if you provide a
     * TimeoutAwareAggregationStrategy then the timeout method is invoked before
     * breaking out. If the timeout is reached with running tasks still
     * remaining, certain tasks for which it is difficult for Camel to shut down
     * in a graceful manner may continue to run. So use this option with a bit
     * of care.
     *
     * @param timeout timeout in millis
     * @return the builder
     */
    public SplitDefinition timeout(long timeout) {
        return timeout(Long.toString(timeout));
    }

    /**
     * Sets a total timeout specified in millis, when using parallel processing.
     * If the Splitter hasn't been able to split and process all the sub
     * messages within the given timeframe, then the timeout triggers and the
     * Splitter breaks out and continues. Notice if you provide a
     * TimeoutAwareAggregationStrategy then the timeout method is invoked before
     * breaking out. If the timeout is reached with running tasks still
     * remaining, certain tasks for which it is difficult for Camel to shut down
     * in a graceful manner may continue to run. So use this option with a bit
     * of care.
     *
     * @param timeout timeout in millis
     * @return the builder
     */
    public SplitDefinition timeout(String timeout) {
        setTimeout(timeout);
        return this;
    }

    /**
     * Shares the {@link org.apache.camel.spi.UnitOfWork} with the parent and
     * each of the sub messages. Splitter will by default not share unit of work
     * between the parent exchange and each splitted exchange. This means each
     * splitted exchange has its own individual unit of work.
     *
     * @return the builder.
     */
    public SplitDefinition shareUnitOfWork() {
        return shareUnitOfWork(true);
    }

    /**
     * Shares the {@link org.apache.camel.spi.UnitOfWork} with the parent and
     * each of the sub messages. Splitter will by default not share unit of work
     * between the parent exchange and each splitted exchange. This means each
     * splitted exchange has its own individual unit of work.
     *
     * @return the builder.
     */
    public SplitDefinition shareUnitOfWork(boolean shareUnitOfWork) {
        return shareUnitOfWork(Boolean.toString(shareUnitOfWork));
    }

    /**
     * Shares the {@link org.apache.camel.spi.UnitOfWork} with the parent and
     * each of the sub messages. Splitter will by default not share unit of work
     * between the parent exchange and each splitted exchange. This means each
     * splitted exchange has its own individual unit of work.
     *
     * @return the builder.
     */
    public SplitDefinition shareUnitOfWork(String shareUnitOfWork) {
        setShareUnitOfWork(shareUnitOfWork);
        return this;
    }

    // Properties
    // -------------------------------------------------------------------------

    /**
     * Expression of how to split the message body, such as as-is, using a
     * tokenizer, or using an xpath.
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
     * Sets the AggregationStrategy to be used to assemble the replies from the
     * splitted messages, into a single outgoing message from the Splitter. By
     * default Camel will use the original incoming message to the splitter
     * (leave it unchanged). You can also use a POJO as the AggregationStrategy
     */
    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
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

    public String getParallelAggregate() {
        return parallelAggregate;
    }

    public void setParallelAggregate(String parallelAggregate) {
        this.parallelAggregate = parallelAggregate;
    }

    public String getStopOnAggregateException() {
        return this.stopOnAggregateException;
    }

    public void setStopOnAggregateException(String stopOnAggregateException) {
        this.stopOnAggregateException = stopOnAggregateException;
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
     * Sets a reference to the AggregationStrategy to be used to assemble the
     * replies from the splitted messages, into a single outgoing message from
     * the Splitter. By default Camel will use the original incoming message to
     * the splitter (leave it unchanged). You can also use a POJO as the
     * AggregationStrategy
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

}
