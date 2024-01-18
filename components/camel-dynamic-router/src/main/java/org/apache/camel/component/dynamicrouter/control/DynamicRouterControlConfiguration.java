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
package org.apache.camel.component.dynamicrouter.control;

import org.apache.camel.Predicate;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

/**
 * Configuration for the {@link DynamicRouterControlEndpoint}.
 */
@UriParams
public class DynamicRouterControlConfiguration {

    /**
     * The control action (subscribe or unsubscribe).
     */
    @UriPath(label = "control", enums = "subscribe,unsubscribe",
             description = "Control channel action: subscribe or unsubscribe")
    @Metadata(required = true)
    private String controlAction;

    /**
     * The channel to subscribe to.
     */
    @UriParam(label = "control", description = "The channel to subscribe to")
    private String subscribeChannel;

    /**
     * The subscription ID. If one is not supplied, one will be generated and returned.
     */
    @UriParam(label = "control", description = "The subscription ID; if unspecified, one will be assigned and returned.")
    private String subscriptionId;

    /**
     * The destination URI where the subscriber will receive matching exchanges.
     */
    @UriParam(label = "control", description = "The destination URI for exchanges that match.")
    private String destinationUri;

    /**
     * The subscription priority. Lower numbers have higher priority.
     */
    @UriParam(label = "control", description = "The subscription priority.")
    private Integer priority;

    /**
     * The {@link Predicate} used to evaluate exchanges for subscribers. Only predicates that can be expressed as a
     * string (e.g., using the Simple language) can be specified via URI param. Other types must be sent as the message
     * body, or as a reference to a bean in the registry via {@link #predicateBean}.
     */
    @UriParam(label = "control", description = "The subscription predicate.")
    private String predicate;

    /**
     * Reference to a {@link Predicate} in the registry.
     */
    @UriParam(label = "control", description = "A Predicate instance in the registry.", javaType = "org.apache.camel.Predicate")
    private Predicate predicateBean;

    /**
     * The expression language for creating the {@link Predicate}. The default is "simple".
     */
    @UriParam(label = "control", defaultValue = "simple", description = "The subscription predicate language.")
    private String expressionLanguage = "simple";

    /**
     * The control action (subscribe or unsubscribe).
     *
     * @return the control action
     */
    public String getControlAction() {
        return controlAction;
    }

    /**
     * The control action (subscribe or unsubscribe) that returns the default value of "subscribe" if null.
     *
     * @return the control action
     */
    public String getControlActionOrDefault() {
        return controlAction == null ? "subscribe" : controlAction;
    }

    /**
     * The control action (subscribe or unsubscribe).
     *
     * @param controlAction the control action -- subscribe or unsubscribe
     */
    public void setControlAction(final String controlAction) {
        this.controlAction = controlAction;
    }

    /**
     * The channel to subscribe to.
     *
     * @return subscribe channel name
     */
    public String getSubscribeChannel() {
        return subscribeChannel;
    }

    /**
     * The channel to subscribe to.
     *
     * @param subscribeChannel subscribe channel name
     */
    public void setSubscribeChannel(final String subscribeChannel) {
        this.subscribeChannel = subscribeChannel;
    }

    /**
     * The subscription ID. If one is not supplied, one will be generated and returned.
     *
     * @return the subscription ID
     */
    public String getSubscriptionId() {
        return subscriptionId;
    }

    /**
     * The subscription ID. If one is not supplied, one will be generated and returned.
     *
     * @param subscriptionId the subscription ID
     */
    public void setSubscriptionId(final String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    /**
     * The destination URI where the subscriber will receive matching exchanges.
     *
     * @return the destination URI
     */
    public String getDestinationUri() {
        return destinationUri;
    }

    /**
     * The destination URI where the subscriber will receive matching exchanges.
     *
     * @param destinationUri the destination URI
     */
    public void setDestinationUri(final String destinationUri) {
        this.destinationUri = destinationUri;
    }

    /**
     * The subscription priority. Lower numbers have higher priority.
     *
     * @return the subscription priority
     */
    public Integer getPriority() {
        return priority;
    }

    /**
     * The subscription priority. Lower numbers have higher priority.
     *
     * @param priority the subscription priority
     */
    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    /**
     * The {@link Predicate} used to evaluate exchanges for subscribers. Only predicates that can be expressed as a
     * string (e.g., using the Simple language) can be specified via URI param. Other types must be sent as the message
     * body, or as a reference to a bean in the registry via {@link #predicateBean}.
     *
     * @return the predicate for evaluating exchanges
     */
    public String getPredicate() {
        return predicate;
    }

    /**
     * The {@link Predicate} used to evaluate exchanges for subscribers. Only predicates that can be expressed as a
     * string (e.g., using the Simple language) can be specified via URI param. Other types must be sent as the message
     * body, or as a reference to a bean in the registry via {@link #predicateBean}.
     *
     * @param predicate the predicate for evaluating exchanges
     */
    public void setPredicate(final String predicate) {
        this.predicate = predicate;
    }

    /**
     * Reference to a {@link Predicate} in the registry.
     *
     * @return the predicate for evaluating exchanges
     */
    public Predicate getPredicateBean() {
        return predicateBean;
    }

    /**
     * Reference to a {@link Predicate} in the registry.
     *
     * @param predicateBean the predicate for evaluating exchanges
     */
    public void setPredicateBean(final Predicate predicateBean) {
        this.predicateBean = predicateBean;
    }

    /**
     * The expression language for creating the {@link Predicate}. The default is "simple".
     *
     * @return the expression language name
     */
    public String getExpressionLanguage() {
        return expressionLanguage;
    }

    /**
     * The expression language for creating the {@link Predicate}. The default is "simple".
     *
     * @param expressionLanguage the expression language name
     */
    public void setExpressionLanguage(final String expressionLanguage) {
        this.expressionLanguage = expressionLanguage;
    }

    public DynamicRouterControlConfiguration() {
        // Default constructor is empty because we need an instance, and
        // then things can be populated through mutator methods.
    }
}
