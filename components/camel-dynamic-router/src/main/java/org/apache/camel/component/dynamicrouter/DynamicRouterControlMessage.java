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

import java.io.Serializable;

import org.apache.camel.Predicate;

/**
 * The control channel message to provide the conditions under which a recipient should receive an exchange for
 * processing. Provides Builder classes for fluent construction of subscribe and unsubscribe messages.
 */
public class DynamicRouterControlMessage implements Serializable {

    /**
     * The type of control message.
     */
    private final ControlMessageType messageType;

    /**
     * The ID of the subscription, which must be provided for all message types.
     */
    private final String id;

    /**
     * The channel of the Dynamic Router.
     */
    private final String channel;

    /**
     * The priority level of this subscription, where a lower number indicates higher priority.
     */
    private final int priority;

    /**
     * The endpoint to send the exchange if the predicate evaluates to true.
     */
    private final String endpoint;

    /**
     * The rule expression to determine if an exchange should be sent to the endpoint.
     *
     * @see <a href="https://camel.apache.org/manual/predicate.html">Camel Predicates documentation</a>
     */
    private final Predicate predicate;

    /**
     * Constructor that sets all properties.
     *
     * @param messageType the type of this message
     * @param id          the id for the subscription, and the only way to unsubscribe
     * @param channel     the channel of the dynamic router
     * @param priority    the priority of this rule, relative to other rules
     * @param endpoint    the endpoint URI to send qualifying messages
     * @param predicate   {@link Predicate} used to determine if the exchange should be sent to the endpoint URI
     */
    public DynamicRouterControlMessage(
                                       final ControlMessageType messageType,
                                       final String id,
                                       final String channel,
                                       int priority,
                                       final String endpoint,
                                       final Predicate predicate) {
        this.messageType = messageType;
        this.id = id;
        this.channel = channel;
        this.priority = priority;
        this.endpoint = endpoint;
        this.predicate = predicate;
    }

    /**
     * Get the message type.
     *
     * @return the message type
     */
    public ControlMessageType getMessageType() {
        return this.messageType;
    }

    /**
     * Get the subscription ID.
     *
     * @return the subscription id
     */
    public String getId() {
        return id;
    }

    /**
     * Get the Dynamic Router channel.
     *
     * @return the Dynamic Router channel
     */
    public String getChannel() {
        return channel;
    }

    /**
     * Get the priority.
     *
     * @return the priority
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Get the predicate.
     *
     * @return the predicate
     * @see    <a href="https://camel.apache.org/manual/predicate.html">Camel Predicates documentation</a>
     */
    public Predicate getPredicate() {
        return this.predicate;
    }

    /**
     * Get the endpoint
     *
     * @return the endpoint
     */
    public String getEndpoint() {
        return this.endpoint;
    }

    /**
     * The type of message received on the control channel.
     */
    public enum ControlMessageType implements Serializable {
        /**
         * For subscribing to receive exchanges.
         */
        SUBSCRIBE,

        /**
         * For unsubscribing to stop receiving exchanges.
         */
        UNSUBSCRIBE
    }

    /**
     * A Builder class for creating a subscribe message.
     */
    public static class SubscribeMessageBuilder {

        private String id;

        private String channel;

        private int priority;

        private String endpoint;

        private Predicate predicate;

        public SubscribeMessageBuilder id(String id) {
            this.id = id;
            return this;
        }

        public SubscribeMessageBuilder channel(String channel) {
            this.channel = channel;
            return this;
        }

        public SubscribeMessageBuilder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public SubscribeMessageBuilder endpointUri(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public SubscribeMessageBuilder predicate(Predicate predicate) {
            this.predicate = predicate;
            return this;
        }

        public DynamicRouterControlMessage build() {
            if (id == null || id.isEmpty() ||
                    channel == null || channel.isEmpty() ||
                    endpoint == null || endpoint.isEmpty() ||
                    predicate == null) {
                throw new IllegalArgumentException(
                        "Unsubscribe messages must be created with an id, a channel, an endpoint URI, and a predicate");
            }
            return new DynamicRouterControlMessage(ControlMessageType.SUBSCRIBE, id, channel, priority, endpoint, predicate);
        }
    }

    /**
     * A Builder class for creating an unsubscribe message.
     */
    public static class UnsubscribeMessageBuilder {

        private String id;

        private String channel;

        public UnsubscribeMessageBuilder id(String id) {
            this.id = id;
            return this;
        }

        public UnsubscribeMessageBuilder channel(String channel) {
            this.channel = channel;
            return this;
        }

        public DynamicRouterControlMessage build() {
            if (id == null || id.isEmpty() || channel == null || channel.isEmpty()) {
                throw new IllegalArgumentException("Unsubscribe messages must be created with an id and a channel");
            }
            return new DynamicRouterControlMessage(ControlMessageType.UNSUBSCRIBE, id, channel, 0, null, null);
        }
    }
}
