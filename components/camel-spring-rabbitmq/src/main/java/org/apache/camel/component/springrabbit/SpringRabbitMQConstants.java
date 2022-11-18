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
    @Metadata(description = "The exchange key.", javaType = "String")
    public static final String ROUTING_OVERRIDE_KEY = "CamelSpringRabbitmqRoutingOverrideKey";
    @Metadata(description = "The exchange name.", javaType = "String")
    public static final String EXCHANGE_OVERRIDE_NAME = "CamelSpringRabbitmqExchangeOverrideName";
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
    public static final String QUEUE_TYPE = "x-queue-type";

    private SpringRabbitMQConstants() {
        // Constants class
    }
}
