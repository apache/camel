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
 *
 * @param messageType The type of control message.
 * @param id          The ID of the subscription, which must be provided for all message types.
 * @param channel     The channel of the Dynamic Router.
 * @param priority    The priority level of this subscription, where a lower number indicates higher priority.
 * @param endpoint    The endpoint to send the exchange if the predicate evaluates to true.
 * @param predicate   The rule expression to determine if an exchange should be sent to the endpoint.
 */
public record DynamicRouterControlMessage(ControlMessageType messageType, String id, String channel, int priority,
        String endpoint, Predicate predicate) implements Serializable {

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
