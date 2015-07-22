/**
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

public final class RabbitMQConstants {
    // TODO need to change the constant which is start with camel
    public static final String ROUTING_KEY = "rabbitmq.ROUTING_KEY";
    public static final String EXCHANGE_NAME = "rabbitmq.EXCHANGE_NAME";
    public static final String CONTENT_TYPE = "rabbitmq.CONTENT_TYPE";
    public static final String PRIORITY = "rabbitmq.PRIORITY";
    public static final String DELIVERY_TAG = "rabbitmq.DELIVERY_TAG";
    public static final String CORRELATIONID = "rabbitmq.CORRELATIONID";
    public static final String MESSAGE_ID = "rabbitmq.MESSAGE_ID";
    public static final String DELIVERY_MODE = "rabbitmq.DELIVERY_MODE";
    public static final String USERID = "rabbitmq.USERID";
    public static final String CLUSTERID = "rabbitmq.CLUSTERID";
    public static final String REQUEST_TIMEOUT = "rabbitmq.REQUEST_TIMEOUT";
    public static final String REPLY_TO = "rabbitmq.REPLY_TO";
    public static final String CONTENT_ENCODING = "rabbitmq.CONTENT_ENCODING";
    public static final String TYPE = "rabbitmq.TYPE";
    public static final String EXPIRATION = "rabbitmq.EXPIRATION";
    public static final String TIMESTAMP = "rabbitmq.TIMESTAMP";
    public static final String APP_ID = "rabbitmq.APP_ID";
    public static final String REQUEUE = "rabbitmq.REQUEUE";
    public static final String MANDATORY = "rabbitmq.MANDATORY";
    public static final String IMMEDIATE = "rabbitmq.IMMEDIATE";
    public static final String RABBITMQ_DEAD_LETTER_EXCHANGE = "x-dead-letter-exchange";
    public static final String RABBITMQ_DEAD_LETTER_ROUTING_KEY = "x-dead-letter-routing-key";
    
    private RabbitMQConstants() {
        //Constants class
    }
}
