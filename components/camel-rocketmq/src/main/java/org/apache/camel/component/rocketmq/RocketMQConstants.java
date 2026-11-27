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

    @Metadata(label = "consumer",
              description = "The topic of the message. The consumer sets it, and so does the producer for the reply of"
                            + " an InOut exchange.",
              javaType = "String")
    public static final String TOPIC = "CamelRocketMQTopic";
    @Metadata(label = "consumer", description = "The tag of the message, or null if it has none.", javaType = "String")
    public static final String TAG = "CamelRocketMQTag";
    @Metadata(label = "consumer",
              description = "The keys of the message, separated by a space if there are several, or null if it has"
                            + " none.",
              javaType = "String", important = true)
    public static final String KEY = "CamelRocketMQKey";
    @Metadata(label = "producer",
              description = "The topic to send the message to. It replaces the topic name of the endpoint.",
              javaType = "String")
    public static final String OVERRIDE_TOPIC_NAME = "CamelRocketMQOverrideTopicName";
    @Metadata(label = "producer",
              description = "The tag of the message to send. It replaces the sendTag option.",
              javaType = "String")
    public static final String OVERRIDE_TAG = "CamelRocketMQOverrideTag";
    @Metadata(label = "producer",
              description = "The keys of the message to send, separated by a space if there are several. For an InOut"
                            + " exchange the producer adds a key that it generates to match the reply.",
              javaType = "String")
    public static final String OVERRIDE_MESSAGE_KEY = "CamelRocketMQOverrideMessageKey";
    @Metadata(label = "consumer", description = "The name of the broker that holds the message queue of the message.",
              javaType = "String")
    public static final String BROKER_NAME = "CamelRocketMQBrokerName";
    @Metadata(label = "consumer", description = "The ID of the message queue of the topic that holds the message.",
              javaType = "int")
    public static final String QUEUE_ID = "CamelRocketMQQueueId";
    @Metadata(label = "consumer", description = "The size in bytes of the message as the broker stored it.", javaType = "int")
    public static final String STORE_SIZE = "CamelRocketMQStoreSize";
    @Metadata(label = "consumer", description = "The offset of the message in its message queue.", javaType = "long")
    public static final String QUEUE_OFFSET = "CamelRocketMQQueueOffset";
    @Metadata(label = "consumer",
              description = "The system flags of the message, a bit set that RocketMQ uses for compression and the"
                            + " transaction type.",
              javaType = "int")
    public static final String SYS_FLAG = "CamelRocketMQSysFlag";
    @Metadata(label = "consumer", description = "The time when the producer sent the message, in milliseconds since the epoch.",
              javaType = "long")
    public static final String BORN_TIMESTAMP = "CamelRocketMQBornTimestamp";
    @Metadata(label = "consumer", description = "The address of the producer client that sent the message.",
              javaType = "java.net.SocketAddress")
    public static final String BORN_HOST = "CamelRocketMQBornHost";
    @Metadata(label = "consumer", description = "The time when the broker stored the message, in milliseconds since the epoch.",
              javaType = "long")
    public static final String STORE_TIMESTAMP = "CamelRocketMQStoreTimestamp";
    @Metadata(label = "consumer", description = "The address of the broker that stored the message.",
              javaType = "java.net.SocketAddress")
    public static final String STORE_HOST = "CamelRocketMQStoreHost";
    @Metadata(label = "consumer",
              description = "The ID of the message: the unique ID that the producer client created for it, or else the"
                            + " offset message ID that the broker built from its address and the commit log offset.",
              javaType = "String")
    public static final String MSG_ID = "CamelRocketMQMsgId";
    @Metadata(label = "consumer", description = "The physical offset of the message in the commit log of the broker.",
              javaType = "long")
    public static final String COMMIT_LOG_OFFSET = "CamelRocketMQCommitLogOffset";
    @Metadata(label = "consumer",
              description = "The CRC32 checksum of the message body, which the broker computed when it stored the"
                            + " message.",
              javaType = "int")
    public static final String BODY_CRC = "CamelRocketMQBodyCrc";
    @Metadata(label = "consumer",
              description = "How many times RocketMQ has delivered the message again after a failed consumption, 0 on"
                            + " the first delivery.",
              javaType = "int")
    public static final String RECONSUME_TIMES = "CamelRocketMQReconsumeTimes";
    @Metadata(label = "consumer",
              description = "For a message of a transaction, the commit log offset of its prepared (half) message.",
              javaType = "long")
    public static final String PREPARED_TRANSACTION_OFFSET = "CamelRocketMQPreparedTransactionOffset";

    private RocketMQConstants() {
    }
}
