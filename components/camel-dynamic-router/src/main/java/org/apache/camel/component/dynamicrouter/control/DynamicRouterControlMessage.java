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

import org.apache.camel.Endpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * A message to control the Dynamic Router. This class serves as an alternative to supplying these details through
 * request params in the control channel {@link Endpoint} URI.
 */
public final class DynamicRouterControlMessage {

    /**
     * The Dynamic Router channel to subscribe to.
     */
    private String subscribeChannel;

    /**
     * The subscription ID; if unspecified, one will be assigned and returned.
     */
    private String subscriptionId;

    /**
     * The destination URI for exchanges that match.
     */
    private String destinationUri;

    /**
     * The subscription priority.
     */
    private int priority;

    /**
     * The subscription predicate to determine if an exchange should be routed.
     */
    private String predicateBean;

    /**
     * The name of a Predicate bean in the registry.
     */
    private String predicate;

    /**
     * The subscription predicate language. The default is "simple".
     */
    private String expressionLanguage;

    /**
     * Default constructor for a new Dynamic Router control message without any values.
     */
    public DynamicRouterControlMessage() {
        // Default constructor
    }

    /**
     * Constructor for a new Dynamic Router control message with the given values.
     *
     * @param subscribeChannel   the channel to subscribe to
     * @param subscriptionId     the subscription ID
     * @param destinationUri     the destination URI
     * @param priority           the subscription priority
     * @param predicateBean      the name of a predicate bean in the registry
     * @param predicate          the predicate expression
     * @param expressionLanguage the subscription predicate language
     */
    public DynamicRouterControlMessage(String subscribeChannel, String subscriptionId,
                                       String destinationUri, int priority, String predicateBean, String predicate,
                                       String expressionLanguage) {
        this.subscribeChannel = subscribeChannel;
        this.subscriptionId = subscriptionId;
        this.destinationUri = destinationUri;
        this.priority = priority;
        this.predicateBean = predicateBean;
        this.predicate = predicate;
        this.expressionLanguage = (ObjectHelper.isEmpty(expressionLanguage) && ObjectHelper.isNotEmpty(predicate))
                ? "simple" : expressionLanguage;
    }

    /**
     * Constructor for a new Dynamic Router control message with the given values.
     *
     * @param builder the {@link Builder} to construct the new {@link DynamicRouterControlMessage}.
     */
    private DynamicRouterControlMessage(Builder builder) {
        subscribeChannel = builder.subscribeChannel;
        subscriptionId = builder.subscriptionId;
        destinationUri = builder.destinationUri;
        priority = builder.priority;
        predicateBean = builder.predicateBean;
        predicate = builder.predicate;
        expressionLanguage = builder.expressionLanguage;
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
     * The subscription ID. If one is not supplied, one will be generated and returned.
     *
     * @return the subscription ID
     */
    public String getSubscriptionId() {
        return subscriptionId;
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
     * The subscription priority.
     *
     * @return the subscription priority
     */
    public int getPriority() {
        return priority;
    }

    /**
     * The name of a predicate bean in the registry.
     *
     * @return the subscription predicate
     */
    public String getPredicateBean() {
        return predicateBean;
    }

    /**
     * The predicate expression as a String that is interpreted in the context of the {@link #expressionLanguage}
     * parameter.
     *
     * @return the predicate expression
     */
    public String getPredicate() {
        return predicate;
    }

    /**
     * The subscription predicate language. Used to interpret the String expression provided by the {@link #predicate}
     * parameter.
     *
     * @return the subscription predicate language
     */
    public String getExpressionLanguage() {
        return expressionLanguage;
    }

    /**
     * {@code DynamicRouterControlMessage} builder static inner class.
     */
    public static final class Builder {

        private String subscribeChannel;

        private String subscriptionId;

        private String destinationUri;

        private int priority;

        private String predicateBean;

        private String predicate;

        private String expressionLanguage;

        /**
         * Instantiates a new {@code DynamicRouterControlMessage.Builder}.
         */
        private Builder() {
        }

        /**
         * Returns a {@code DynamicRouterControlMessage.Builder} object that can be used to create a new
         * {@code DynamicRouterControlMessage}.
         *
         * @return a {@code DynamicRouterControlMessage.Builder} object that can be used to create a new
         *         {@code DynamicRouterControlMessage}
         */
        public static Builder newBuilder() {
            return new Builder();
        }

        /**
         * Sets the {@code subscribeChannel} and returns a reference to this Builder enabling method chaining.
         *
         * @param  val the {@code subscribeChannel} to set
         * @return     a reference to this Builder
         */
        public Builder subscribeChannel(String val) {
            subscribeChannel = val;
            return this;
        }

        /**
         * Sets the {@code subscriptionId} and returns a reference to this Builder enabling method chaining.
         *
         * @param  val the {@code subscriptionId} to set
         * @return     a reference to this Builder
         */
        public Builder subscriptionId(String val) {
            subscriptionId = val;
            return this;
        }

        /**
         * Sets the {@code destinationUri} and returns a reference to this Builder enabling method chaining.
         *
         * @param  val the {@code destinationUri} to set
         * @return     a reference to this Builder
         */
        public Builder destinationUri(String val) {
            destinationUri = val;
            return this;
        }

        /**
         * Sets the {@code priority} and returns a reference to this Builder enabling method chaining.
         *
         * @param  val the {@code priority} to set
         * @return     a reference to this Builder
         */
        public Builder priority(int val) {
            priority = val;
            return this;
        }

        /**
         * Sets the {@code predicateBean} and returns a reference to this Builder enabling method chaining.
         *
         * @param  val the {@code predicateBean} to set
         * @return     a reference to this Builder
         */
        public Builder predicateBean(String val) {
            predicateBean = val;
            return this;
        }

        /**
         * Sets the {@code predicate} and returns a reference to this Builder enabling method chaining.
         *
         * @param  val the {@code predicate} to set
         * @return     a reference to this Builder
         */
        public Builder predicate(String val) {
            predicate = val;
            return this;
        }

        /**
         * Sets the {@code expressionLanguage} and returns a reference to this Builder enabling method chaining.
         *
         * @param  val the {@code expressionLanguage} to set
         * @return     a reference to this Builder
         */
        public Builder expressionLanguage(String val) {
            expressionLanguage = val;
            return this;
        }

        /**
         * Returns a {@code DynamicRouterControlMessage} built from the parameters previously set.
         *
         * @return a {@code DynamicRouterControlMessage} built with parameters of this
         *         {@code DynamicRouterControlMessage.Builder}
         */
        public DynamicRouterControlMessage build() {
            return new DynamicRouterControlMessage(this);
        }
    }
}
