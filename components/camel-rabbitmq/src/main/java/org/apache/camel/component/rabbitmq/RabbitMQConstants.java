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

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;

public final class RabbitMQConstants {

    @Metadata(description = "*Consumer:* The routing key that was used to receive the message, or the routing key\n" +
                            "that will be used when producing a message\n " +
                            "*Producer:* The routing key that will be used when sending the message",
              javaType = "String")
    public static final String ROUTING_KEY = "CamelRabbitmqRoutingKey";
    @Metadata(description = "Used for force sending the message to this exchange instead of the endpoint configured name on the producer",
              javaType = "String")
    public static final String EXCHANGE_OVERRIDE_NAME = "CamelRabbitmqExchangeOverrideName";
    @Metadata(description = "*Consumer:* The exchange the message was received from\n " +
                            "*Producer:* The exchange the message was received from",
              javaType = "String")
    public static final String EXCHANGE_NAME = "CamelRabbitmqExchangeName";
    @Metadata(description = "The contentType to set on the RabbitMQ message", javaType = "String")
    public static final String CONTENT_TYPE = "CamelRabbitmqContentType";
    @Metadata(description = "The priority header to set on the RabbitMQ message", javaType = "int")
    public static final String PRIORITY = "CamelRabbitmqPriority";
    @Metadata(description = "The rabbitmq delivery tag of the received message", javaType = "long")
    public static final String DELIVERY_TAG = "CamelRabbitmqDeliveryTag";
    @Metadata(description = "Whether the message is a redelivered", javaType = "boolean")
    public static final String REDELIVERY_TAG = "CamelRabbitmqRedeliveryTag";
    @Metadata(description = "The correlationId to set on the RabbitMQ message.", javaType = "String")
    public static final String CORRELATIONID = "CamelRabbitmqCorrelationId";
    @Metadata(description = "The message id to set on the RabbitMQ message.", javaType = "String")
    public static final String MESSAGE_ID = "CamelRabbitmqMessageId";
    @Metadata(description = "If the message should be persistent or not", javaType = "Integer")
    public static final String DELIVERY_MODE = "CamelRabbitmqDeliveryMode";
    @Metadata(description = "The userId to set on the RabbitMQ message", javaType = "String")
    public static final String USERID = "CamelRabbitmqUserId";
    @Metadata(description = "The clusterId to set on the RabbitMQ message", javaType = "String")
    public static final String CLUSTERID = "CamelRabbitmqClusterId";
    @Metadata(description = "The timeout for waiting for a reply when using the InOut Exchange Pattern (in milliseconds)",
              javaType = "long")
    public static final String REQUEST_TIMEOUT = "CamelRabbitmqRequestTimeout";
    @Metadata(description = "The replyTo to set on the RabbitMQ message", javaType = "String")
    public static final String REPLY_TO = "CamelRabbitmqReplyTo";
    @Metadata(description = "The contentEncoding to set on the RabbitMQ message", javaType = "String")
    public static final String CONTENT_ENCODING = "CamelRabbitmqContentEncoding";
    @Metadata(description = "The type to set on the RabbitMQ message", javaType = "String")
    public static final String TYPE = "CamelRabbitmqType";
    @Metadata(description = "The expiration to set on the RabbitMQ message", javaType = "String")
    public static final String EXPIRATION = "CamelRabbitmqExpiration";
    @Metadata(description = "The timestamp to set on the RabbitMQ message", javaType = "java.util.Date")
    public static final String TIMESTAMP = "CamelRabbitmqTimestamp";
    @Metadata(description = "The appId to set on the RabbitMQ message", javaType = "String")
    public static final String APP_ID = "CamelRabbitmqAppId";
    @Metadata(description = "This is used by the consumer to control rejection of the\n" +
                            "message. When the consumer is complete processing the exchange, and if\n" +
                            "the exchange failed, then the consumer is going to reject the message\n" +
                            "from the RabbitMQ broker. The value of this header controls this\n" +
                            "behavior. If the value is false (by default) then the message is\n" +
                            "discarded/dead-lettered. If the value is true, then the message is\n" +
                            "re-queued.",
              javaType = "boolean")
    public static final String REQUEUE = "CamelRabbitmqRequeue";
    @Metadata(description = "The flag telling the server how to react if the message cannot be routed to a queue.",
              javaType = "Boolean")
    public static final String MANDATORY = "CamelRabbitmqMandatory";
    @Metadata(description = "The flag telling the server how to react if the message cannot be routed to a queue consumer immediately.",
              javaType = "Boolean")
    public static final String IMMEDIATE = "CamelRabbitmqImmediate";
    @Metadata(description = "The timestamp of the RabbitMQ message", javaType = "long")
    public static final String MESSAGE_TIMESTAMP = Exchange.MESSAGE_TIMESTAMP;
    public static final String RABBITMQ_DEAD_LETTER_EXCHANGE = "x-dead-letter-exchange";
    public static final String RABBITMQ_DEAD_LETTER_ROUTING_KEY = "x-dead-letter-routing-key";
    public static final String RABBITMQ_DIRECT_REPLY_EXCHANGE = "";
    public static final String RABBITMQ_DIRECT_REPLY_ROUTING_KEY = "amq.rabbitmq.reply-to";
    public static final String RABBITMQ_QUEUE_LENGTH_LIMIT_KEY = "x-max-length";
    public static final String RABBITMQ_QUEUE_MAX_PRIORITY_KEY = "x-max-priority";
    public static final String RABBITMQ_QUEUE_MESSAGE_TTL_KEY = "x-message-ttl";
    public static final String RABBITMQ_QUEUE_TTL_KEY = "x-expires";
    public static final String RABBITMQ_QUEUE_SINGLE_ACTIVE_CONSUMER_KEY = "x-single-active-consumer";

    public static final Set<String> BASIC_AMQP_PROPERTIES = Stream
            .of(CONTENT_TYPE, PRIORITY, MESSAGE_ID, CLUSTERID, REPLY_TO, CORRELATIONID, DELIVERY_MODE, USERID, TYPE,
                    CONTENT_ENCODING, EXPIRATION, APP_ID, TIMESTAMP)
            .collect(Collectors.toSet());

    private RabbitMQConstants() {
        // Constants class
    }
}
