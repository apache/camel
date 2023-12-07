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
package org.apache.camel.component.springrabbit;

import org.apache.camel.spi.Metadata;

public final class SpringRabbitMQConstants {

    public static final String DEFAULT_EXCHANGE_NAME = "default";

    public static final String CHANNEL = "CamelSpringRabbitmqChannel";
    @Metadata(description = "To override the endpoint configuration's routing key.", javaType = "String", label = "producer")
    public static final String ROUTING_OVERRIDE_KEY = "CamelSpringRabbitmqRoutingOverrideKey";
    @Metadata(description = "To override the endpoint configuration's exchange name.", javaType = "String", label = "producer")
    public static final String EXCHANGE_OVERRIDE_NAME = "CamelSpringRabbitmqExchangeOverrideName";
    @Metadata(description = "Whether the message was previously delivered and requeued.", javaType = "Boolean",
              label = "consumer")
    public static final String REDELIVERED = "CamelSpringRabbitmqRedelivered";
    @Metadata(description = "Delivery tag for manual acknowledge mode.", javaType = "long", label = "consumer")
    public static final String DELIVERY_TAG = "CamelSpringRabbitmqDeliveryTag";
    @Metadata(description = "The exchange name that was used when publishing the message.", javaType = "String",
              label = "consumer")
    public static final String EXCHANGE_NAME = "CamelSpringRabbitmqExchangeName";
    @Metadata(description = "The routing key that was used when publishing the message.", javaType = "String",
              label = "consumer")
    public static final String ROUTING_KEY = "CamelSpringRabbitmqRoutingKey";
    @Metadata(description = "The message delivery mode.", javaType = "MessageDeliveryMode", enums = "NON_PERSISTENT,PERSISTENT")
    public static final String DELIVERY_MODE = "CamelSpringRabbitmqDeliveryMode";
    @Metadata(description = "Application-specific message type.", javaType = "String")
    public static final String TYPE = "CamelSpringRabbitmqType";
    @Metadata(description = "The message content type.", javaType = "String")
    public static final String CONTENT_TYPE = "CamelSpringRabbitmqContentType";
    @Metadata(description = "The message content length.", javaType = "long")
    public static final String CONTENT_LENGTH = "CamelSpringRabbitmqContentLength";
    @Metadata(description = "Content encoding used by applications.", javaType = "String")
    public static final String CONTENT_ENCODING = "CamelSpringRabbitmqContentEncoding";
    @Metadata(description = "Arbitrary message id.", javaType = "String")
    public static final String MESSAGE_ID = "CamelSpringRabbitmqMessageId";
    @Metadata(description = "Identifier to correlate RPC responses with requests.", javaType = "String")
    public static final String CORRELATION_ID = "CamelSpringRabbitmqCorrelationId";
    @Metadata(description = "Commonly used to name a callback queue.", javaType = "String")
    public static final String REPLY_TO = "CamelSpringRabbitmqReplyTo";
    @Metadata(description = "Per-message TTL.", javaType = "String")
    public static final String EXPIRATION = "CamelSpringRabbitmqExpiration";
    @Metadata(description = "Application-provided timestamp.", javaType = "Date")
    public static final String TIMESTAMP = "CamelSpringRabbitmqTimestamp";
    @Metadata(description = "Validated user id.", javaType = "String")
    public static final String USER_ID = "CamelSpringRabbitmqUserId";
    @Metadata(description = "The application name.", javaType = "String")
    public static final String APP_ID = "CamelSpringRabbitmqAppId";
    @Metadata(description = "The message priority.", javaType = "Integer")
    public static final String PRIORITY = "CamelSpringRabbitmqPriority";
    @Metadata(description = "The cluster id.", javaType = "String")
    public static final String CLUSTER_ID = "CamelSpringRabbitmqClusterId";

    public static final String DIRECT_MESSAGE_LISTENER_CONTAINER = "DMLC";
    public static final String SIMPLE_MESSAGE_LISTENER_CONTAINER = "SMLC";

    public static final String DEAD_LETTER_EXCHANGE = "x-dead-letter-exchange";
    public static final String DEAD_LETTER_ROUTING_KEY = "x-dead-letter-routing-key";
    public static final String MAX_LENGTH = "x-max-length";
    public static final String MAX_LENGTH_BYTES = "x-max-length-bytes";
    public static final String MAX_PRIORITY = "x-max-priority";
    public static final String MESSAGE_TTL = "x-message-ttl";
    public static final String DELIVERY_LIMIT = "x-delivery-limit";
    public static final String EXPIRES = "x-expires";
    public static final String SINGLE_ACTIVE_CONSUMER = "x-single-active-consumer";

    private SpringRabbitMQConstants() {
        // Constants class
    }
}
