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

package org.apache.camel.component.rocketmq;

import org.apache.camel.spi.Metadata;

public final class RocketMQConstants {

    @Metadata(description = "Broker name", javaType = "String")
    public static final String BROKER_NAME = "rocketmq.BROKER_NAME";
    @Metadata(description = "Queue ID", javaType = "int")
    public static final String QUEUE_ID = "rocketmq.QUEUE_ID";
    @Metadata(description = "Store size", javaType = "int")
    public static final String STORE_SIZE = "rocketmq.STORE_SIZE";
    @Metadata(description = "Queue offset", javaType = "long")
    public static final String QUEUE_OFFSET = "rocketmq.QUEUE_OFFSET";
    @Metadata(description = "Sys flag", javaType = "int")
    public static final String SYS_FLAG = "rocketmq.SYS_FLAG";
    @Metadata(description = "Born timestamp", javaType = "long")
    public static final String BORN_TIMESTAMP = "rocketmq.BORN_TIMESTAMP";
    @Metadata(description = "Born host", javaType = "java.net.SocketAddress")
    public static final String BORN_HOST = "rocketmq.BORN_HOST";
    @Metadata(description = "Store timestamp", javaType = "long")
    public static final String STORE_TIMESTAMP = "rocketmq.STORE_TIMESTAMP";
    @Metadata(description = "Store host", javaType = "java.net.SocketAddress")
    public static final String STORE_HOST = "rocketmq.STORE_HOST";
    @Metadata(description = "Message ID", javaType = "String")
    public static final String MSG_ID = "rocketmq.MSG_ID";
    @Metadata(description = "Commit log offset", javaType = "long")
    public static final String COMMIT_LOG_OFFSET = "rocketmq.COMMIT_LOG_OFFSET";
    @Metadata(description = "Body CRC", javaType = "int")
    public static final String BODY_CRC = "rocketmq.BODY_CRC";
    @Metadata(description = "Reconsume times", javaType = "int")
    public static final String RECONSUME_TIMES = "rocketmq.RECONSUME_TIMES";
    @Metadata(description = "Prepard transaction offset", javaType = "long")
    public static final String PREPARED_TRANSACTION_OFFSET = "rocketmq.PREPARED_TRANSACTION_OFFSET";
    @Metadata(description = "If this header is set, the message will be routed to the topic specified by this header\n" +
                            "instead of the origin topic in endpoint.",
              javaType = "String")
    public static final String OVERRIDE_TOPIC_NAME = "rocketmq.OVERRIDE_TOPIC_NAME";
    @Metadata(description = "If this header is set, the message's tag will be set to value specified by this header\n" +
                            "instead of the sendTag defined in endpoint.",
              javaType = "String")
    public static final String OVERRIDE_TAG = "rocketmq.OVERRIDE_TAG";
    @Metadata(description = "Set keys for the message. When using in-out pattern,\n" +
                            "the value will be prepended to the generated keys",
              javaType = "String")
    public static final String OVERRIDE_MESSAGE_KEY = "rocketmq.OVERRIDE_MESSAGE_KEY";
    @Metadata(description = "Tag of message", javaType = "String")
    public static final String TAG = "rocketmq.TAG";
    @Metadata(description = "Topic of message", javaType = "String")
    public static final String TOPIC = "rocketmq.TOPIC";
    @Metadata(description = "Key of message", javaType = "String")
    public static final String KEY = "rocketmq.KEY";

    private RocketMQConstants() {
    }
}
