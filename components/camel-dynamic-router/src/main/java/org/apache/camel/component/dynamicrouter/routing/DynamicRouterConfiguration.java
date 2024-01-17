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
package org.apache.camel.component.dynamicrouter.routing;

import java.util.concurrent.ExecutorService;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Processor;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

import static org.apache.camel.component.dynamicrouter.routing.DynamicRouterConstants.MODE_ALL_MATCH;
import static org.apache.camel.component.dynamicrouter.routing.DynamicRouterConstants.MODE_FIRST_MATCH;

/**
 * This class encapsulates all configuration items for the Dynamic Router EIP component.
 */
@UriParams
public class DynamicRouterConfiguration {

    /**
     * Channel for the Dynamic Router. For example, if the Dynamic Router URI is "dynamic-router://test", then the
     * channel is "test". Channels are a way of keeping routing participants, their rules, and exchanges logically
     * separate from the participants, rules, and exchanges on other channels. This can be seen as analogous to VLANs in
     * networking.
     */
    @UriPath(name = "channel", label = "common", description = "Channel of the Dynamic Router")
    @Metadata(required = true)
    private String channel;

    /**
     * Sets the behavior of the Dynamic Router when routing participants are selected to receive an incoming exchange.
     * If the mode is "firstMatch", then the exchange is routed only to the first participant that has a matching
     * predicate. If the mode is "allMatch", then the exchange is routed to all participants that have a matching
     * predicate.
     */
    @UriParam(label = "common", defaultValue = MODE_FIRST_MATCH, enums = MODE_FIRST_MATCH + "," + MODE_ALL_MATCH,
              description = "Recipient mode: firstMatch or allMatch")
    private String recipientMode = MODE_FIRST_MATCH;

    /**
     * Sets whether synchronous processing should be strictly used. When enabled then the same thread is used to
     * continue routing after the multicast is complete, even if parallel processing is enabled.
     */
    @UriParam(label = "common", defaultValue = "false")
    private boolean synchronous;

    /**
     * Flag to log a warning if no predicates match for an exchange.
     */
    @UriParam(label = "common", defaultValue = "false")
    private boolean warnDroppedMessage;

    /**
     * If enabled, then sending via multicast occurs concurrently. Note that the caller thread will still wait until all
     * messages have been fully processed before it continues. It is only the sending and processing of the replies from
     * the multicast recipients that happens concurrently. When parallel processing is enabled, then the Camel routing
     * engine will continue processing using the last used thread from the parallel thread pool. However, if you want to
     * use the original thread that called the multicast, then make sure to enable the synchronous option as well.
     */
    @UriParam(label = "common", defaultValue = "false")
    private boolean parallelProcessing;

    /**
     * If enabled then the aggregate method on AggregationStrategy can be called concurrently. Notice that this would
     * require the implementation of AggregationStrategy to be implemented as thread-safe. By default, this is false,
     * meaning that Camel synchronizes the call to the aggregate method. Though, in some use-cases, this can be used to
     * archive higher performance when the AggregationStrategy is implemented as thread-safe.
     */
    @UriParam(label = "common", defaultValue = "false")
    private boolean parallelAggregate;

    /**
     * Will stop further processing if an exception or failure occurred during processing of an
     * {@link org.apache.camel.Exchange} and the caused exception will be thrown. Will also stop if processing the
     * exchange failed (has a fault message), or an exception was thrown and handled by the error handler (such as using
     * onException). In all situations, the multicast will stop further processing. This is the same behavior as in the
     * pipeline that is used by the routing engine. The default behavior is to not stop, but to continue processing
     * until the end.
     */
    @UriParam(label = "common", defaultValue = "false")
    private boolean stopOnException;

    /**
     * Ignore the invalid endpoint exception when attempting to create a producer with an invalid endpoint.
     */
    @UriParam(label = "common", defaultValue = "false")
    private boolean ignoreInvalidEndpoints;

    /**
     * If enabled, then Camel will process replies out-of-order (e.g., in the order they come back). If disabled, Camel
     * will process replies in the same order as defined by the multicast.
     */
    @UriParam(label = "common", defaultValue = "false")
    private boolean streaming;

    /**
     * Sets a total timeout specified in milliseconds, when using parallel processing. If the Multicast has not been
     * able to send and process all replies within the given timeframe, then the timeout triggers and the Multicast
     * breaks out and continues. Notice that, if you provide a TimeoutAwareAggregationStrategy, then the timeout method
     * is invoked before breaking out. If the timeout is reached with running tasks still remaining, certain tasks (for
     * which it is difficult for Camel to shut down in a graceful manner) may continue to run. So use this option with a
     * bit of care.
     */
    @UriParam(label = "common", defaultValue = "-1")
    private long timeout;

    /**
     * When caching producer endpoints, this is the size of the cache. Default is 100.
     */
    @UriParam(label = "common", defaultValue = "100")
    private int cacheSize = 100;

    /**
     * Uses the Processor when preparing the {@link org.apache.camel.Exchange} to be sent. This can be used to
     * deep-clone messages that should be sent, or to provide any custom logic that is needed before the exchange is
     * sent. This is the name of a bean in the registry.
     */
    @UriParam(label = "common")
    private String onPrepare;

    /**
     * Uses the Processor when preparing the {@link org.apache.camel.Exchange} to be sent. This can be used to
     * deep-clone messages that should be sent, or to provide any custom logic that is needed before the exchange is
     * sent. This is a {@link Processor} instance.
     */
    @UriParam(label = "common")
    private Processor onPrepareProcessor;

    /**
     * Shares the {@link org.apache.camel.spi.UnitOfWork} with the parent and each of the sub messages. Multicast will,
     * by default, not share a unit of work between the parent exchange and each multicasted exchange. This means each
     * sub exchange has its own individual unit of work.
     */
    @UriParam(label = "common", defaultValue = "false")
    private boolean shareUnitOfWork;

    /**
     * Refers to a custom Thread Pool to be used for parallel processing. Notice that, if you set this option, then
     * parallel processing is automatically implied, and you do not have to enable that option in addition to this one.
     */
    @UriParam(label = "common")
    private String executorService;

    /**
     * Refers to a custom Thread Pool to be used for parallel processing. Notice that, if you set this option, then
     * parallel processing is automatically implied, and you do not have to enable that option in addition to this one.
     */
    @UriParam(label = "common")
    private ExecutorService executorServiceBean;

    /**
     * Refers to an AggregationStrategy to be used to assemble the replies from the multicasts, into a single outgoing
     * message from the Multicast. By default, Camel will use the last reply as the outgoing message. You can also use a
     * POJO as the AggregationStrategy.
     */
    @UriParam(label = "common")
    private String aggregationStrategy;

    /**
     * Refers to an AggregationStrategy to be used to assemble the replies from the multicasts, into a single outgoing
     * message from the Multicast. By default, Camel will use the last reply as the outgoing message. You can also use a
     * POJO as the AggregationStrategy.
     */
    @UriParam(label = "common")
    private AggregationStrategy aggregationStrategyBean;

    /**
     * You can use a POJO as the AggregationStrategy. This refers to the name of the method that aggregates the
     * exchanges.
     */
    @UriParam(label = "common")
    private String aggregationStrategyMethodName;

    /**
     * If this option is false then the aggregate method is not used if there was no data to enrich. If this option is
     * true then null values is used as the oldExchange (when no data to enrich), when using POJOs as the
     * AggregationStrategy
     */
    @UriParam(label = "common")
    private boolean aggregationStrategyMethodAllowNull;

    /**
     * Channel for the Dynamic Router. For example, if the Dynamic Router URI is "dynamic-router://test", then the
     * channel is "test". Channels are a way of keeping routing participants, their rules, and exchanges logically
     * separate from the participants, rules, and exchanges on other channels. This can be seen as analogous to VLANs in
     * networking.
     *
     * @return the channel
     */
    public String getChannel() {
        return channel;
    }

    /**
     * Channel for the Dynamic Router. For example, if the Dynamic Router URI is "dynamic-router://test", then the
     * channel is "test". Channels are a way of keeping routing participants, their rules, and exchanges logically
     * separate from the participants, rules, and exchanges on other channels. This can be seen as analogous to VLANs in
     * networking.
     *
     * @param channel the channel
     */
    public void setChannel(final String channel) {
        this.channel = channel;
    }

    /**
     * Gets the behavior of the Dynamic Router when routing participants are selected to receive an incoming exchange.
     * If the mode is "firstMatch", then the exchange is routed only to the first participant that has a matching
     * predicate. If the mode is "allMatch", then the exchange is routed to all participants that have a matching
     * predicate.
     *
     * @return the recipient mode
     */
    public String getRecipientMode() {
        return recipientMode;
    }

    /**
     * Sets the behavior of the Dynamic Router when routing participants are selected to receive an incoming exchange.
     * If the mode is "firstMatch", then the exchange is routed only to the first participant that has a matching
     * predicate. If the mode is "allMatch", then the exchange is routed to all participants that have a matching
     * predicate.
     *
     * @param recipientMode the recipient mode
     */
    public void setRecipientMode(final String recipientMode) {
        this.recipientMode = recipientMode;
    }

    /**
     * Flag to ensure synchronous processing.
     *
     * @return true if processing will be synchronous, false otherwise
     */
    public boolean isSynchronous() {
        return synchronous;
    }

    /**
     * Flag to ensure synchronous processing.
     *
     * @param synchronous flag if processing will be synchronous
     */
    public void setSynchronous(boolean synchronous) {
        this.synchronous = synchronous;
    }

    /**
     * Flag to log a warning if no predicates match for an exchange.
     *
     * @return true if logging a warning when no predicates match an exchange, false otherwise
     */
    public boolean isWarnDroppedMessage() {
        return warnDroppedMessage;
    }

    /**
     * Flag to log a warning if no predicates match for an exchange.
     *
     * @param warnDroppedMessage flag to log warnings when no predicates match
     */
    public void setWarnDroppedMessage(boolean warnDroppedMessage) {
        this.warnDroppedMessage = warnDroppedMessage;
    }

    /**
     * Gets whether the multicast should be streaming or not.
     *
     * @return true if streaming, false otherwise
     */
    public boolean isStreaming() {
        return streaming;
    }

    /**
     * Sets whether the multicast should be streaming or not.
     *
     * @param streaming flag to set whether the multicast should be streaming or not
     */
    public void setStreaming(boolean streaming) {
        this.streaming = streaming;
    }

    /**
     * Gets whether invalid endpoints should be ignored.
     *
     * @return true if invalid endpoints should be ignored, false otherwise
     */
    public boolean isIgnoreInvalidEndpoints() {
        return ignoreInvalidEndpoints;
    }

    /**
     * Sets whether invalid endpoints should be ignored.
     *
     * @param ignoreInvalidEndpoints flag to set whether invalid endpoints should be ignored
     */
    public void setIgnoreInvalidEndpoints(boolean ignoreInvalidEndpoints) {
        this.ignoreInvalidEndpoints = ignoreInvalidEndpoints;
    }

    /**
     * Gets whether parallel processing is enabled.
     *
     * @return true if parallel processing is enabled, false otherwise
     */
    public boolean isParallelProcessing() {
        return parallelProcessing;
    }

    /**
     * Sets whether parallel processing is enabled.
     *
     * @param parallelProcessing flag to set whether parallel processing is enabled
     */
    public void setParallelProcessing(boolean parallelProcessing) {
        this.parallelProcessing = parallelProcessing;
    }

    /**
     * Gets whether parallel aggregation is enabled.
     *
     * @return true if parallel aggregation is enabled, false otherwise
     */
    public boolean isParallelAggregate() {
        return parallelAggregate;
    }

    /**
     * Sets whether parallel aggregation is enabled.
     *
     * @param parallelAggregate flag to set whether parallel aggregation is enabled
     */
    public void setParallelAggregate(boolean parallelAggregate) {
        this.parallelAggregate = parallelAggregate;
    }

    /**
     * Gets whether the multicast should stop on exception.
     *
     * @return true if the multicast should stop on exception, false otherwise
     */
    public boolean isStopOnException() {
        return stopOnException;
    }

    /**
     * Sets whether the multicast should stop on exception.
     *
     * @param stopOnException flag to set whether the multicast should stop on exception
     */
    public void setStopOnException(boolean stopOnException) {
        this.stopOnException = stopOnException;
    }

    /**
     * Gets the executor service.
     *
     * @return the executor service
     */
    public String getExecutorService() {
        return executorService;
    }

    /**
     * Sets the executor service.
     *
     * @param executorService the executor service
     */
    public void setExecutorService(String executorService) {
        this.executorService = executorService;
    }

    /**
     * Gets the executor service bean.
     *
     * @return the executor service bean
     */
    public ExecutorService getExecutorServiceBean() {
        return executorServiceBean;
    }

    /**
     * Sets the executor service bean.
     *
     * @param executorServiceBean the executor service bean
     */
    public void setExecutorServiceBean(ExecutorService executorServiceBean) {
        this.executorServiceBean = executorServiceBean;
    }

    /**
     * Gets the aggregation strategy.
     *
     * @return the aggregation strategy
     */
    public String getAggregationStrategy() {
        return aggregationStrategy;
    }

    /**
     * Sets the aggregation strategy.
     *
     * @param aggregationStrategy the aggregation strategy
     */
    public void setAggregationStrategy(String aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }

    /**
     * Gets the timeout.
     *
     * @return the timeout
     */
    public long getTimeout() {
        return timeout;
    }

    /**
     * Sets the timeout.
     *
     * @param timeout the timeout
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    /**
     * Gets the cache size.
     *
     * @return the cache size
     */
    public int getCacheSize() {
        return cacheSize;
    }

    /**
     * Sets the cache size.
     *
     * @param cacheSize the cache size
     */
    public void setCacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
    }

    /**
     * Gets the on prepare processor reference.
     *
     * @return the on prepare processor reference
     */
    public String getOnPrepare() {
        return onPrepare;
    }

    /**
     * Sets the on prepare processor reference.
     *
     * @param onPrepare the on prepare processor reference
     */
    public void setOnPrepare(String onPrepare) {
        this.onPrepare = onPrepare;
    }

    /**
     * Gets the on prepare processor.
     *
     * @return the on prepare processor
     */
    public Processor getOnPrepareProcessor() {
        return onPrepareProcessor;
    }

    /**
     * Sets the on prepare processor.
     *
     * @param onPrepare the on prepare processor
     */
    public void setOnPrepareProcessor(Processor onPrepare) {
        this.onPrepareProcessor = onPrepare;
    }

    /**
     * Gets the share unit of work flag.
     *
     * @return the share unit of work flag
     */
    public boolean isShareUnitOfWork() {
        return shareUnitOfWork;
    }

    /**
     * Sets the share unit of work flag.
     *
     * @param shareUnitOfWork the share unit of work flag
     */
    public void setShareUnitOfWork(boolean shareUnitOfWork) {
        this.shareUnitOfWork = shareUnitOfWork;
    }

    /**
     * Gets the aggregation strategy method name.
     *
     * @return the aggregation strategy method name
     */
    public String getAggregationStrategyMethodName() {
        return aggregationStrategyMethodName;
    }

    /**
     * Sets the aggregation strategy method name.
     *
     * @param aggregationStrategyMethodName the aggregation strategy method name
     */
    public void setAggregationStrategyMethodName(String aggregationStrategyMethodName) {
        this.aggregationStrategyMethodName = aggregationStrategyMethodName;
    }

    /**
     * Gets whether the aggregation strategy method allows null.
     *
     * @return true if the aggregation strategy method allows null, false otherwise
     */
    public boolean isAggregationStrategyMethodAllowNull() {
        return aggregationStrategyMethodAllowNull;
    }

    /**
     * Sets whether the aggregation strategy method allows null.
     *
     * @param aggregationStrategyMethodAllowNull flag to set whether the aggregation strategy method allows null
     */
    public void setAggregationStrategyMethodAllowNull(boolean aggregationStrategyMethodAllowNull) {
        this.aggregationStrategyMethodAllowNull = aggregationStrategyMethodAllowNull;
    }

    /**
     * Gets the aggregation strategy bean.
     *
     * @return the aggregation strategy bean
     */
    public AggregationStrategy getAggregationStrategyBean() {
        return aggregationStrategyBean;
    }

    /**
     * Sets the aggregation strategy bean.
     *
     * @param aggregationStrategyBean the aggregation strategy bean
     */
    public void setAggregationStrategyBean(AggregationStrategy aggregationStrategyBean) {
        this.aggregationStrategyBean = aggregationStrategyBean;
    }
}
