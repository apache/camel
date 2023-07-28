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
import org.apache.camel.builder.ProcessClause;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Route messages to a number of dynamically specified recipients
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "recipientList")
@XmlAccessorType(XmlAccessType.FIELD)
public class RecipientListDefinition<Type extends ProcessorDefinition<Type>> extends ExpressionNode
        implements ExecutorServiceAwareDefinition<RecipientListDefinition<Type>> {

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
    @Metadata(javaType = "java.time.Duration", defaultValue = "0")
    private String timeout;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.util.concurrent.ExecutorService")
    private String executorService;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String stopOnException;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String ignoreInvalidEndpoints;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String streaming;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "org.apache.camel.Processor")
    private String onPrepare;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Integer")
    private String cacheSize;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String shareUnitOfWork;

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
     * @param  delimiter the delimiter
     * @return           the builder
     */
    public RecipientListDefinition<Type> delimiter(String delimiter) {
        setDelimiter(delimiter);
        return this;
    }

    /**
     * Sets the AggregationStrategy to be used to assemble the replies from the recipients, into a single outgoing
     * message from the RecipientList. By default Camel will use the last reply as the outgoing message. You can also
     * use a POJO as the AggregationStrategy
     */
    public RecipientListDefinition<Type> aggregationStrategy(AggregationStrategy aggregationStrategy) {
        setAggregationStrategy(aggregationStrategy);
        return this;
    }

    /**
     * Sets a reference to the AggregationStrategy to be used to assemble the replies from the recipients, into a single
     * outgoing message from the RecipientList. By default Camel will use the last reply as the outgoing message. You
     * can also use a POJO as the AggregationStrategy
     */
    public RecipientListDefinition<Type> aggregationStrategy(String aggregationStrategy) {
        setAggregationStrategy(aggregationStrategy);
        return this;
    }

    /**
     * This option can be used to explicit declare the method name to use, when using POJOs as the AggregationStrategy.
     *
     * @param  methodName the method name to call
     * @return            the builder
     */
    public RecipientListDefinition<Type> aggregationStrategyMethodName(String methodName) {
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
    public RecipientListDefinition<Type> aggregationStrategyMethodAllowNull() {
        setAggregationStrategyMethodAllowNull(Boolean.toString(true));
        return this;
    }

    /**
     * Ignore the invalidate endpoint exception when try to create a producer with that endpoint
     *
     * @return the builder
     */
    public RecipientListDefinition<Type> ignoreInvalidEndpoints() {
        setIgnoreInvalidEndpoints(Boolean.toString(true));
        return this;
    }

    /**
     * If enabled then sending messages to the recipients occurs concurrently. Note the caller thread will still wait
     * until all messages has been fully processed, before it continues. Its only the sending and processing the replies
     * from the recipients which happens concurrently.
     *
     * When parallel processing is enabled, then the Camel routing engin will continue processing using last used thread
     * from the parallel thread pool. However, if you want to use the original thread that called the recipient list,
     * then make sure to enable the synchronous option as well.
     *
     * @return the builder
     */
    public RecipientListDefinition<Type> parallelProcessing() {
        setParallelProcessing(Boolean.toString(true));
        return this;
    }

    /**
     * If enabled then sending messages to the recipients occurs concurrently. Note the caller thread will still wait
     * until all messages has been fully processed, before it continues. Its only the sending and processing the replies
     * from the recipients which happens concurrently.
     *
     * When parallel processing is enabled, then the Camel routing engin will continue processing using last used thread
     * from the parallel thread pool. However, if you want to use the original thread that called the recipient list,
     * then make sure to enable the synchronous option as well.
     *
     * @return the builder
     */
    public RecipientListDefinition<Type> parallelProcessing(String parallelProcessing) {
        setParallelProcessing(parallelProcessing);
        return this;
    }

    /**
     * If enabled then sending messages to the recipients occurs concurrently. Note the caller thread will still wait
     * until all messages has been fully processed, before it continues. Its only the sending and processing the replies
     * from the recipients which happens concurrently.
     *
     * When parallel processing is enabled, then the Camel routing engin will continue processing using last used thread
     * from the parallel thread pool. However, if you want to use the original thread that called the recipient list,
     * then make sure to enable the synchronous option as well.
     *
     * @return the builder
     */
    public RecipientListDefinition<Type> parallelProcessing(boolean parallelProcessing) {
        return parallelProcessing(Boolean.toString(parallelProcessing));
    }

    /**
     * If enabled then the aggregate method on AggregationStrategy can be called concurrently. Notice that this would
     * require the implementation of AggregationStrategy to be implemented as thread-safe. By default this is false
     * meaning that Camel synchronizes the call to the aggregate method. Though in some use-cases this can be used to
     * archive higher performance when the AggregationStrategy is implemented as thread-safe.
     *
     * @return the builder
     */
    public RecipientListDefinition<Type> parallelAggregate() {
        return parallelAggregate(Boolean.toString(true));
    }

    /**
     * If enabled then the aggregate method on AggregationStrategy can be called concurrently. Notice that this would
     * require the implementation of AggregationStrategy to be implemented as thread-safe. By default this is false
     * meaning that Camel synchronizes the call to the aggregate method. Though in some use-cases this can be used to
     * archive higher performance when the AggregationStrategy is implemented as thread-safe.
     *
     * @return the builder
     */
    public RecipientListDefinition<Type> parallelAggregate(boolean parallelAggregate) {
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
    public RecipientListDefinition<Type> parallelAggregate(String parallelAggregate) {
        setParallelAggregate(parallelAggregate);
        return this;
    }

    /**
     * Sets whether synchronous processing should be strictly used. When enabled then the same thread is used to
     * continue routing after the recipient list is complete, even if parallel processing is enabled.
     *
     * @return the builder
     */
    public RecipientListDefinition<Type> synchronous() {
        return synchronous(true);
    }

    /**
     * Sets whether synchronous processing should be strictly used. When enabled then the same thread is used to
     * continue routing after the recipient list is complete, even if parallel processing is enabled.
     *
     * @return the builder
     */
    public RecipientListDefinition<Type> synchronous(boolean synchronous) {
        return synchronous(Boolean.toString(synchronous));
    }

    /**
     * Sets whether synchronous processing should be strictly used. When enabled then the same thread is used to
     * continue routing after the recipient list is complete, even if parallel processing is enabled.
     *
     * @return the builder
     */
    public RecipientListDefinition<Type> synchronous(String synchronous) {
        setSynchronous(synchronous);
        return this;
    }

    /**
     * If enabled then Camel will process replies out-of-order, eg in the order they come back. If disabled, Camel will
     * process replies in the same order as defined by the recipient list.
     *
     * @return the builder
     */
    public RecipientListDefinition<Type> streaming() {
        setStreaming(Boolean.toString(true));
        return this;
    }

    /**
     * Will now stop further processing if an exception or failure occurred during processing of an
     * {@link org.apache.camel.Exchange} and the caused exception will be thrown.
     * <p/>
     * Will also stop if processing the exchange failed (has a fault message) or an exception was thrown and handled by
     * the error handler (such as using onException). In all situations the recipient list will stop further processing.
     * This is the same behavior as in pipeline, which is used by the routing engine.
     * <p/>
     * The default behavior is to <b>not</b> stop but continue processing till the end
     *
     * @return the builder
     */
    public RecipientListDefinition<Type> stopOnException() {
        setStopOnException(Boolean.toString(true));
        return this;
    }

    /**
     * To use a custom Thread Pool to be used for parallel processing. Notice if you set this option, then parallel
     * processing is automatic implied, and you do not have to enable that option as well.
     */
    @Override
    public RecipientListDefinition<Type> executorService(ExecutorService executorService) {
        this.executorServiceBean = executorService;
        return this;
    }

    /**
     * Refers to a custom Thread Pool to be used for parallel processing. Notice if you set this option, then parallel
     * processing is automatic implied, and you do not have to enable that option as well.
     */
    @Override
    public RecipientListDefinition<Type> executorService(String executorService) {
        setExecutorService(executorService);
        return this;
    }

    /**
     * Uses the {@link Processor} when preparing the {@link org.apache.camel.Exchange} to be used send. This can be used
     * to deep-clone messages that should be send, or any custom logic needed before the exchange is send.
     *
     * @param  onPrepare the processor
     * @return           the builder
     */
    public RecipientListDefinition<Type> onPrepare(Processor onPrepare) {
        setOnPrepareProcessor(onPrepare);
        return this;
    }

    /**
     * Sets the {@link Processor} when preparing the {@link org.apache.camel.Exchange} to be used send using a fluent
     * buidler.
     */
    public ProcessClause<RecipientListDefinition<Type>> onPrepare() {
        ProcessClause<RecipientListDefinition<Type>> clause = new ProcessClause<>(this);
        setOnPrepareProcessor(clause);
        return clause;
    }

    /**
     * Uses the {@link Processor} when preparing the {@link org.apache.camel.Exchange} to be send. This can be used to
     * deep-clone messages that should be send, or any custom logic needed before the exchange is send.
     *
     * @param  ref reference to the processor to lookup in the {@link org.apache.camel.spi.Registry}
     * @return     the builder
     */
    public RecipientListDefinition<Type> onPrepare(String ref) {
        setOnPrepare(ref);
        return this;
    }

    /**
     * Sets a total timeout specified in millis, when using parallel processing. If the Recipient List hasn't been able
     * to send and process all replies within the given timeframe, then the timeout triggers and the Recipient List
     * breaks out and continues. Notice if you provide a TimeoutAwareAggregationStrategy then the timeout method is
     * invoked before breaking out. If the timeout is reached with running tasks still remaining, certain tasks for
     * which it is difficult for Camel to shut down in a graceful manner may continue to run. So use this option with a
     * bit of care.
     *
     * @param  timeout timeout in millis
     * @return         the builder
     */
    public RecipientListDefinition<Type> timeout(long timeout) {
        setTimeout(Long.toString(timeout));
        return this;
    }

    /**
     * Shares the {@link org.apache.camel.spi.UnitOfWork} with the parent and each of the sub messages. Recipient List
     * will by default not share unit of work between the parent exchange and each recipient exchange. This means each
     * sub exchange has its own individual unit of work.
     *
     * @return the builder.
     */
    public RecipientListDefinition<Type> shareUnitOfWork() {
        setShareUnitOfWork(Boolean.toString(true));
        return this;
    }

    /**
     * Sets the maximum size used by the {@link org.apache.camel.spi.ProducerCache} which is used to cache and reuse
     * producers when using this recipient list, when uris are reused.
     * <p>
     * Beware that when using dynamic endpoints then it affects how well the cache can be utilized. If each dynamic
     * endpoint is unique then its best to turn off caching by setting this to -1, which allows Camel to not cache both
     * the producers and endpoints; they are regarded as prototype scoped and will be stopped and discarded after use.
     * This reduces memory usage as otherwise producers/endpoints are stored in memory in the caches.
     * <p>
     * However if there are a high degree of dynamic endpoints that have been used before, then it can benefit to use
     * the cache to reuse both producers and endpoints and therefore the cache size can be set accordingly or rely on
     * the default size (1000).
     * <p>
     * If there is a mix of unique and used before dynamic endpoints, then setting a reasonable cache size can help
     * reduce memory usage to avoid storing too many non frequent used producers.
     *
     * @param  cacheSize the cache size, use <tt>0</tt> for default cache size, or <tt>-1</tt> to turn cache off.
     * @return           the builder
     */
    public RecipientListDefinition<Type> cacheSize(int cacheSize) {
        setCacheSize(Integer.toString(cacheSize));
        return this;
    }

    /**
     * Sets the maximum size used by the {@link org.apache.camel.spi.ProducerCache} which is used to cache and reuse
     * producers when using this recipient list, when uris are reused.
     * <p>
     * Beware that when using dynamic endpoints then it affects how well the cache can be utilized. If each dynamic
     * endpoint is unique then its best to turn off caching by setting this to -1, which allows Camel to not cache both
     * the producers and endpoints; they are regarded as prototype scoped and will be stopped and discarded after use.
     * This reduces memory usage as otherwise producers/endpoints are stored in memory in the caches.
     * <p>
     * However if there are a high degree of dynamic endpoints that have been used before, then it can benefit to use
     * the cache to reuse both producers and endpoints and therefore the cache size can be set accordingly or rely on
     * the default size (1000).
     * <p>
     * If there is a mix of unique and used before dynamic endpoints, then setting a reasonable cache size can help
     * reduce memory usage to avoid storing too many non frequent used producers.
     *
     * @param  cacheSize the cache size, use <tt>0</tt> for default cache size, or <tt>-1</tt> to turn cache off.
     * @return           the builder
     */
    public RecipientListDefinition<Type> cacheSize(String cacheSize) {
        setCacheSize(cacheSize);
        return this;
    }

    // Properties
    // -------------------------------------------------------------------------

    public AggregationStrategy getAggregationStrategyBean() {
        return aggregationStrategyBean;
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
     * Expression that returns which endpoints (url) to send the message to (the recipients). If the expression return
     * an empty value then the message is not sent to any recipients.
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

    public String getIgnoreInvalidEndpoints() {
        return ignoreInvalidEndpoints;
    }

    public void setIgnoreInvalidEndpoints(String ignoreInvalidEndpoints) {
        this.ignoreInvalidEndpoints = ignoreInvalidEndpoints;
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

    public void setAggregationStrategy(String aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }

    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategyBean = aggregationStrategy;
    }

    public String getAggregationStrategyMethodName() {
        return aggregationStrategyMethodName;
    }

    public void setAggregationStrategyMethodName(String aggregationStrategyMethodName) {
        this.aggregationStrategyMethodName = aggregationStrategyMethodName;
    }

    public String getAggregationStrategyMethodAllowNull() {
        return aggregationStrategyMethodAllowNull;
    }

    public void setAggregationStrategyMethodAllowNull(String aggregationStrategyMethodAllowNull) {
        this.aggregationStrategyMethodAllowNull = aggregationStrategyMethodAllowNull;
    }

    public String getStreaming() {
        return streaming;
    }

    public void setStreaming(String streaming) {
        this.streaming = streaming;
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

    public Processor getOnPrepareProcessor() {
        return onPrepareProcessor;
    }

    public void setOnPrepareProcessor(Processor onPrepareProcessor) {
        this.onPrepareProcessor = onPrepareProcessor;
    }

    public String getShareUnitOfWork() {
        return shareUnitOfWork;
    }

    public void setShareUnitOfWork(String shareUnitOfWork) {
        this.shareUnitOfWork = shareUnitOfWork;
    }

    public String getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(String cacheSize) {
        this.cacheSize = cacheSize;
    }

    public String getParallelAggregate() {
        return parallelAggregate;
    }

    public void setParallelAggregate(String parallelAggregate) {
        this.parallelAggregate = parallelAggregate;
    }

    public String getExecutorService() {
        return executorService;
    }

    public void setExecutorService(String executorService) {
        this.executorService = executorService;
    }
}
