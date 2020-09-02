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
package org.apache.camel.component.rabbitmq;

public enum RabbitMQConstants {

    // TODO need to change the constant which is start with camel
    ROUTING_KEY("rabbitmq.ROUTING_KEY", "The routing key that will be used when sending the message"),
    EXCHANGE_OVERRIDE_NAME("rabbitmq.EXCHANGE_OVERRIDE_NAME", "Used for force sending the message to this exchange instead of the endpoint configured name on the producer"),
    EXCHANGE_NAME("rabbitmq.EXCHANGE_NAME", "The exchange the message was received from"),
    CONTENT_TYPE("rabbitmq.CONTENT_TYPE", "The contentType to set on the RabbitMQ message"),
    PRIORITY("rabbitmq.PRIORITY", "The priority header to set on the RabbitMQ message"),
    DELIVERY_TAG("rabbitmq.DELIVERY_TAG", "The rabbitmq delivery tag of the received message"),
    REDELIVERY_TAG("rabbitmq.REDELIVERY_TAG", "Whether the message is a redelivered"),
    CORRELATIONID("rabbitmq.CORRELATIONID", "The correlationId to set on the RabbitMQ message"),
    MESSAGE_ID("rabbitmq.MESSAGE_ID", "The message id to set on the RabbitMQ message"),
    DELIVERY_MODE("rabbitmq.DELIVERY_MODE", "If the message should be persistent or not"),
    USERID("rabbitmq.USERID", "The userId to set on the RabbitMQ message"),
    CLUSTERID("rabbitmq.CLUSTERID", "The clusterId to set on the RabbitMQ message"),
    REQUEST_TIMEOUT("rabbitmq.REQUEST_TIMEOUT", ""),
    REPLY_TO("rabbitmq.REPLY_TO", "The replyTo to set on the RabbitMQ message"),
    CONTENT_ENCODING("rabbitmq.CONTENT_ENCODING", "The contentEncoding to set on the RabbitMQ message"),
    TYPE("rabbitmq.TYPE", "The type to set on the RabbitMQ message"),
    EXPIRATION("rabbitmq.EXPIRATION", "The expiration to set on the RabbitMQ message"),
    TIMESTAMP("rabbitmq.TIMESTAMP", "The timestamp to set on the RabbitMQ message"),
    APP_ID("rabbitmq.APP_ID", "The appId to set on the RabbitMQ message"),
    REQUEUE("rabbitmq.REQUEUE", "This is used by the consumer to control rejection of the message. When the consumer is complete processing the exchange, and if the exchange failed, then the consumer is going to reject the message from the RabbitMQ broker. The value of this header controls this behavior. If the value is false (by default) then the message is discarded/dead-lettered. If the value is true, then the message is re-queued."),
    MANDATORY("rabbitmq.MANDATORY", "true if the 'mandatory' flag is to be set"),
    IMMEDIATE("rabbitmq.IMMEDIATE", "true if the 'immediate' flag is to be set. Note that the RabbitMQ server does not support this flag."),
    RABBITMQ_DEAD_LETTER_EXCHANGE("x-dead-letter-exchange", "The dead letter exchange for a queue"),
    RABBITMQ_DEAD_LETTER_ROUTING_KEY("x-dead-letter-routing-key", "The routing key to be used when dead-lettering message"),
    RABBITMQ_DIRECT_REPLY_EXCHANGE("", ""),
    RABBITMQ_DIRECT_REPLY_ROUTING_KEY("amq.rabbitmq.reply-to", "The RPC server should publish to the default exchange (\"\") with the routing key set to this value (i.e. just as if it were sending to a reply queue as usual). The message will then be sent straight to the client consumer."),
    RABBITMQ_QUEUE_LENGTH_LIMIT_KEY("x-max-length", "Maximum number of messages can be set by supplying this property for the queue declaration argument with a non-negative integer value."),
    RABBITMQ_QUEUE_MAX_PRIORITY_KEY("x-max-priority", ""),
    RABBITMQ_QUEUE_MESSAGE_TTL_KEY("x-message-ttl", "Message TTL(time to live) that can be set for a given queue in milliseconds."),
    RABBITMQ_QUEUE_TTL_KEY("x-expires", "Expiry time for a given queue. Queues will expire after a period of time only when they are not used (e.g. do not have consumers). This feature can be used together with the auto-delete queue property."),
    RABBITMQ_QUEUE_SINGLE_ACTIVE_CONSUMER_KEY("x-single-active-consumer", "Single active consumer allows to have only one consumer at a time consuming from a queue and to fail over to another registered consumer in case the active one is cancelled or dies. ");

    private final String key;
    private final String description;

    RabbitMQConstants(String key, String description) {
        this.key = key;
        this.description = description;
    }

    public String key() {
        return key;
    }

    public String description() {
        return description;
    }

    @Override
    public String toString() {
        return key;
    }
}
