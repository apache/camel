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

    @Metadata(label = "consumer", description = "Topic of message", javaType = "String")
    public static final String TOPIC = "CamelRocketMQTopic";
    @Metadata(label = "consumer", description = "Tag of message", javaType = "String")
    public static final String TAG = "CamelRocketMQTag";
    @Metadata(label = "consumer", description = "Key of message", javaType = "String", important = true)
    public static final String KEY = "CamelRocketMQKey";
    @Metadata(label = "producer",
              description = "If this header is set, the message will be routed to the topic specified by this header\n" +
                            "instead of the origin topic in endpoint.",
              javaType = "String")
    public static final String OVERRIDE_TOPIC_NAME = "CamelRocketMQOverrideTopicName";
    @Metadata(label = "producer",
              description = "If this header is set, the message's tag will be set to value specified by this header\n" +
                            "instead of the sendTag defined in endpoint.",
              javaType = "String")
    public static final String OVERRIDE_TAG = "CamelRocketMQOverrideTag";
    @Metadata(label = "producer", description = "Set keys for the message. When using in-out pattern,\n" +
                                                "the value will be prepended to the generated keys",
              javaType = "String")
    public static final String OVERRIDE_MESSAGE_KEY = "CamelRocketMQOverrideMessageKey";
    @Metadata(label = "consumer", description = "Broker name", javaType = "String")
    public static final String BROKER_NAME = "CamelRocketMQBrokerName";
    @Metadata(label = "consumer", description = "Queue ID", javaType = "int")
    public static final String QUEUE_ID = "CamelRocketMQQueueId";
    @Metadata(label = "consumer", description = "Store size", javaType = "int")
    public static final String STORE_SIZE = "CamelRocketMQStoreSize";
    @Metadata(label = "consumer", description = "Queue offset", javaType = "long")
    public static final String QUEUE_OFFSET = "CamelRocketMQQueueOffset";
    @Metadata(label = "consumer", description = "Sys flag", javaType = "int")
    public static final String SYS_FLAG = "CamelRocketMQSysFlag";
    @Metadata(label = "consumer", description = "Born timestamp", javaType = "long")
    public static final String BORN_TIMESTAMP = "CamelRocketMQBornTimestamp";
    @Metadata(label = "consumer", description = "Born host", javaType = "java.net.SocketAddress")
    public static final String BORN_HOST = "CamelRocketMQBornHost";
    @Metadata(label = "consumer", description = "Store timestamp", javaType = "long")
    public static final String STORE_TIMESTAMP = "CamelRocketMQStoreTimestamp";
    @Metadata(label = "consumer", description = "Store host", javaType = "java.net.SocketAddress")
    public static final String STORE_HOST = "CamelRocketMQStoreHost";
    @Metadata(label = "consumer", description = "Msg ID", javaType = "String")
    public static final String MSG_ID = "CamelRocketMQMsgId";
    @Metadata(label = "consumer", description = "Commit log offset", javaType = "long")
    public static final String COMMIT_LOG_OFFSET = "CamelRocketMQCommitLogOffset";
    @Metadata(label = "consumer", description = "Body CRC", javaType = "int")
    public static final String BODY_CRC = "CamelRocketMQBodyCrc";
    @Metadata(label = "consumer", description = "Reconsume times", javaType = "int")
    public static final String RECONSUME_TIMES = "CamelRocketMQReconsumeTimes";
    @Metadata(label = "consumer", description = "Prepard transaction offset", javaType = "long")
    public static final String PREPARED_TRANSACTION_OFFSET = "CamelRocketMQPreparedTransactionOffset";

    private RocketMQConstants() {
    }
}
