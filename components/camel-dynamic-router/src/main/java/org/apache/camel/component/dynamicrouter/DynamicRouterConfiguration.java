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
package org.apache.camel.component.dynamicrouter;

import java.util.Optional;
import java.util.regex.Matcher;

import org.apache.camel.Predicate;
import org.apache.camel.spi.*;

import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.ACTION_GROUP;
import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.CHANNEL_GROUP;
import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.MODE_ALL_MATCH;
import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.MODE_FIRST_MATCH;
import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.PATH_PARAMS_PATTERN;
import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.SUBSCRIBE_GROUP;

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
     * When sending messages to the control channel without using a {@link DynamicRouterControlMessage}, specify the
     * action (subscribe or unsubscribe) by using this URI path variable.
     */
    @UriPath(name = "controlAction", label = "control", enums = "subscribe,unsubscribe",
             description = "Control channel action: subscribe or unsubscribe")
    @Metadata
    private String controlAction;

    /**
     * When sending messages to the control channel without using a {@link DynamicRouterControlMessage}, specify the
     * subscribe channel by using this URI path variable.
     */
    @UriPath(name = "subscribeChannel", label = "control", description = "The channel to subscribe to")
    @Metadata
    private String subscribeChannel;

    /**
     * When sending messages to the control channel without using a {@link DynamicRouterControlMessage}, specify the
     * subscription ID by using this URI param. If one is not supplied, one will be generated and returned.
     */
    @UriParam(label = "control", description = "The subscription ID; if unspecified, one will be assigned and returned.")
    private String subscriptionId;

    /**
     * When sending messages to the control channel without using a {@link DynamicRouterControlMessage}, specify the
     * destination URI by using this URI param.
     */
    @UriParam(label = "control", description = "The destination URI for exchanges that match.")
    private String destinationUri;

    /**
     * When sending messages to the control channel without using a {@link DynamicRouterControlMessage}, specify the
     * subscription priority by using this URI param. Lower numbers have higher priority.
     */
    @UriParam(label = "control", description = "The subscription priority.")
    private Integer priority;

    /**
     * When sending messages to the control channel without using a {@link DynamicRouterControlMessage}, specify the
     * {@link Predicate} by using this URI param. Only predicates that can be expressed as a string (e.g., using the
     * Simple language) can be specified via URI param. Other types must be sent via control channel POJO or as the
     * message body.
     */
    @UriParam(label = "control", description = "The subscription predicate.")
    private String predicate;

    /**
     * When sending messages to the control channel without using a {@link DynamicRouterControlMessage}, specify the
     * {@link Predicate} by using this URI param. Specify the predicate with bean syntax; i.e., #bean:someBeanId
     */
    @UriParam(label = "control", description = "A Predicate instance in the registry.", javaType = "org.apache.camel.Predicate")
    private Predicate predicateBean;

    /**
     * When sending messages to the control channel without using a {@link DynamicRouterControlMessage}, specify the
     * expression language for creating the {@link Predicate} by using this URI param. The default is "simple".
     */
    @UriParam(label = "control", defaultValue = "simple", description = "The subscription predicate language.")
    private String expressionLanguage = "simple";

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
     * The ID of the route.
     */
    @UriParam(label = "common")
    private String routeId;

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
     * Uses the Processor when preparing the {@link org.apache.camel.Exchange} to be sent. This can be used to
     * deep-clone messages that should be sent, or to provide any custom logic that is needed before the exchange is
     * sent.
     */
    @UriParam(label = "common")
    private String onPrepare;

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
     * TODO: not a configuration setting
     */
    @UriParam(label = "common", defaultValue = "false")
    private boolean shutdownExecutorService;

    /**
     * Refers to an AggregationStrategy to be used to assemble the replies from the multicasts, into a single outgoing
     * message from the Multicast. By default, Camel will use the last reply as the outgoing message. You can also use a
     * POJO as the AggregationStrategy.
     */
    @UriParam(label = "common")
    private String aggregationStrategy;

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
     * When sending messages to the control channel without using a {@link DynamicRouterControlMessage}, specify the
     * action (subscribe or unsubscribe) by using this URI path variable.
     *
     * @return the control action -- subscribe or unsubscribe
     */
    public String getControlAction() {
        return controlAction;
    }

    /**
     * When sending messages to the control channel without using a {@link DynamicRouterControlMessage}, specify the
     * action (subscribe or unsubscribe) by using this URI path variable.
     *
     * @param controlAction the control action -- subscribe or unsubscribe
     */
    public void setControlAction(final String controlAction) {
        this.controlAction = controlAction;
    }

    /**
     * When sending messages to the control channel without using a {@link DynamicRouterControlMessage}, specify the
     * subscribe channel by using this URI path variable.
     *
     * @return subscribe channel name
     */
    public String getSubscribeChannel() {
        return subscribeChannel;
    }

    /**
     * When sending messages to the control channel without using a {@link DynamicRouterControlMessage}, specify the
     * subscribe channel by using this URI path variable.
     *
     * @param subscribeChannel subscribe channel name
     */
    public void setSubscribeChannel(final String subscribeChannel) {
        this.subscribeChannel = subscribeChannel;
    }

    /**
     * When sending messages to the control channel without using a {@link DynamicRouterControlMessage}, specify the
     * subscription ID by using this URI param. If one is not supplied, one will be generated and returned.
     *
     * @return the subscription ID
     */
    public String getSubscriptionId() {
        return subscriptionId;
    }

    /**
     * When sending messages to the control channel without using a {@link DynamicRouterControlMessage}, specify the
     * subscription ID by using this URI param. If one is not supplied, one will be generated and returned.
     *
     * @param subscriptionId the subscription ID
     */
    public void setSubscriptionId(final String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    /**
     * When sending messages to the control channel without using a {@link DynamicRouterControlMessage}, specify the
     * destination URI by using this URI param.
     *
     * @return the destination URI
     */
    public String getDestinationUri() {
        return destinationUri;
    }

    /**
     * When sending messages to the control channel without using a {@link DynamicRouterControlMessage}, specify the
     * destination URI by using this URI param.
     *
     * @param destinationUri the destination URI
     */
    public void setDestinationUri(final String destinationUri) {
        this.destinationUri = destinationUri;
    }

    /**
     * When sending messages to the control channel without using a {@link DynamicRouterControlMessage}, specify the
     * subscription priority by using this URI param. Lower numbers have higher priority.
     *
     * @return the subscription priority
     */
    public Integer getPriority() {
        return priority;
    }

    /**
     * When sending messages to the control channel without using a {@link DynamicRouterControlMessage}, specify the
     * subscription priority by using this URI param. Lower numbers have higher priority.
     *
     * @param priority the subscription priority
     */
    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    /**
     * When sending messages to the control channel without using a {@link DynamicRouterControlMessage}, specify the
     * {@link Predicate} by using this URI param. Only predicates that can be expressed as a string (e.g., using the
     * Simple language) can be specified via URI param. Other types must be sent via control channel POJO or as the
     * message body.
     *
     * @return the predicate for evaluating exchanges
     */
    public String getPredicate() {
        return predicate;
    }

    /**
     * When sending messages to the control channel without using a {@link DynamicRouterControlMessage}, specify the
     * {@link Predicate} by using this URI param. Only predicates that can be expressed as a string (e.g., using the
     * Simple language) can be specified via URI param. Other types must be sent via control channel POJO or as the
     * message body.
     *
     * @param predicate the predicate for evaluating exchanges
     */
    public void setPredicate(final String predicate) {
        this.predicate = predicate;
    }

    /**
     * When sending messages to the control channel without using a {@link DynamicRouterControlMessage}, specify the
     * {@link Predicate} by using this URI param. Only predicates that can be expressed as a string (e.g., using the
     * Simple language) can be specified via URI param. Other types must be sent via control channel POJO or as the
     * message body.
     *
     * @return the predicate for evaluating exchanges
     */
    public Predicate getPredicateBean() {
        return predicateBean;
    }

    /**
     * When sending messages to the control channel without using a {@link DynamicRouterControlMessage}, specify the
     * {@link Predicate} by using this URI param. Only predicates that can be expressed as a string (e.g., using the
     * Simple language) can be specified via URI param. Other types must be sent via control channel POJO or as the
     * message body.
     *
     * @param predicateBean the predicate for evaluating exchanges
     */
    public void setPredicateBean(final Predicate predicateBean) {
        this.predicateBean = predicateBean;
    }

    /**
     * When sending messages to the control channel without using a {@link DynamicRouterControlMessage}, specify the
     * expression language for creating the {@link Predicate} by using this URI param. The default is "simple".
     *
     * @return the expression language name
     */
    public String getExpressionLanguage() {
        return expressionLanguage;
    }

    /**
     * When sending messages to the control channel without using a {@link DynamicRouterControlMessage}, specify the
     * expression language for creating the {@link Predicate} by using this URI param. The default is "simple".
     *
     * @param expressionLanguage the expression language name
     */
    public void setExpressionLanguage(final String expressionLanguage) {
        this.expressionLanguage = expressionLanguage;
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

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(final String routeId) {
        this.routeId = routeId;
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

    public boolean isStreaming() {
        return streaming;
    }

    public void setStreaming(boolean streaming) {
        this.streaming = streaming;
    }

    public boolean isIgnoreInvalidEndpoints() {
        return ignoreInvalidEndpoints;
    }

    public void setIgnoreInvalidEndpoints(boolean ignoreInvalidEndpoints) {
        this.ignoreInvalidEndpoints = ignoreInvalidEndpoints;
    }

    public boolean isParallelProcessing() {
        return parallelProcessing;
    }

    public void setParallelProcessing(boolean parallelProcessing) {
        this.parallelProcessing = parallelProcessing;
    }

    public boolean isParallelAggregate() {
        return parallelAggregate;
    }

    public void setParallelAggregate(boolean parallelAggregate) {
        this.parallelAggregate = parallelAggregate;
    }

    public boolean isStopOnException() {
        return stopOnException;
    }

    public void setStopOnException(boolean stopOnException) {
        this.stopOnException = stopOnException;
    }

    public String getExecutorService() {
        return executorService;
    }

    public void setExecutorService(String executorService) {
        this.executorService = executorService;
    }

    public boolean isShutdownExecutorService() {
        return shutdownExecutorService;
    }

    public void setShutdownExecutorService(boolean shutdownExecutorService) {
        this.shutdownExecutorService = shutdownExecutorService;
    }

    public String getAggregationStrategy() {
        return aggregationStrategy;
    }

    public void setAggregationStrategy(String aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public String getOnPrepare() {
        return onPrepare;
    }

    public void setOnPrepare(String onPrepare) {
        this.onPrepare = onPrepare;
    }

    public boolean isShareUnitOfWork() {
        return shareUnitOfWork;
    }

    public void setShareUnitOfWork(boolean shareUnitOfWork) {
        this.shareUnitOfWork = shareUnitOfWork;
    }

    /**
     * Parse the URI path for configuration parameters.
     *
     * @param path the URI path to parse
     */
    public void parsePath(final String path) {
        Optional.ofNullable(path)
                .map(s -> s.isEmpty() ? null : s)
                .ifPresent(p -> {
                    final Matcher matcher = PATH_PARAMS_PATTERN.matcher(p);
                    boolean matches = matcher.matches();
                    if (matches) {
                        setChannel(matcher.group(CHANNEL_GROUP));
                        setControlAction(matcher.group(ACTION_GROUP));
                        setSubscribeChannel(matcher.group(SUBSCRIBE_GROUP));
                    } else {
                        throw new IllegalArgumentException("Illegal syntax for a Dynamic Router URI: " + path);
                    }
                });
    }
}
