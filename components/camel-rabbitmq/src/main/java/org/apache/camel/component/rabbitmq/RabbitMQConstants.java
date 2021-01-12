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

public final class RabbitMQConstants {

    public static final String ROUTING_KEY = "CamelRabbitmqRoutingKey";
    public static final String EXCHANGE_OVERRIDE_NAME = "CamelRabbitmqExchangeOverrideName";
    public static final String EXCHANGE_NAME = "CamelRabbitmqExchangeName";
    public static final String CONTENT_TYPE = "CamelRabbitmqContentType";
    public static final String PRIORITY = "CamelRabbitmqPriority";
    public static final String DELIVERY_TAG = "CamelRabbitmqDeliveryTag";
    public static final String REDELIVERY_TAG = "CamelRabbitmqRedeliveryTag";
    public static final String CORRELATIONID = "CamelRabbitmqCorrelationId";
    public static final String MESSAGE_ID = "CamelRabbitmqMessageId";
    public static final String DELIVERY_MODE = "CamelRabbitmqDeliveryMode";
    public static final String USERID = "CamelRabbitmqUserId";
    public static final String CLUSTERID = "CamelRabbitmqClusterId";
    public static final String REQUEST_TIMEOUT = "CamelRabbitmqRequestTimeout";
    public static final String REPLY_TO = "CamelRabbitmqReplyTo";
    public static final String CONTENT_ENCODING = "CamelRabbitmqContentEncoding";
    public static final String TYPE = "CamelRabbitmqType";
    public static final String EXPIRATION = "CamelRabbitmqExpiration";
    public static final String TIMESTAMP = "CamelRabbitmqTimestamp";
    public static final String APP_ID = "CamelRabbitmqAppId";
    public static final String REQUEUE = "CamelRabbitmqRequeue";
    public static final String MANDATORY = "CamelRabbitmqMandatory";
    public static final String IMMEDIATE = "CamelRabbitmqImmediate";
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
