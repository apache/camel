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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

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
     * Flag to ensure synchronous processing.
     */
    @UriParam(label = "common", defaultValue = "false")
    private boolean synchronous;

    /**
     * Flag to log a warning if no predicates match for an exchange.
     */
    @UriParam(label = "common", defaultValue = "false")
    private boolean warnDroppedMessage;

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
