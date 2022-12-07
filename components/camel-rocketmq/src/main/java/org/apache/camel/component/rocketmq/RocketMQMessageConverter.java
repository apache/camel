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

import org.apache.camel.Message;
import org.apache.rocketmq.common.message.MessageExt;

public final class RocketMQMessageConverter {

    private RocketMQMessageConverter() {
    }

    public static void populateHeadersByMessageExt(final Message message, final MessageExt messageExt) {
        message.setHeader(RocketMQConstants.TOPIC, messageExt.getTopic());
        message.setHeader(RocketMQConstants.TAG, messageExt.getTags());
        message.setHeader(RocketMQConstants.KEY, messageExt.getKeys());
        message.setHeader(RocketMQConstants.BROKER_NAME, messageExt.getBrokerName());
        message.setHeader(RocketMQConstants.QUEUE_ID, messageExt.getQueueId());
        message.setHeader(RocketMQConstants.STORE_SIZE, messageExt.getStoreSize());
        message.setHeader(RocketMQConstants.QUEUE_OFFSET, messageExt.getQueueOffset());
        message.setHeader(RocketMQConstants.SYS_FLAG, messageExt.getSysFlag());
        message.setHeader(RocketMQConstants.BORN_TIMESTAMP, messageExt.getBornTimestamp());
        message.setHeader(RocketMQConstants.BORN_HOST, messageExt.getBornHost());
        message.setHeader(RocketMQConstants.STORE_TIMESTAMP, messageExt.getStoreTimestamp());
        message.setHeader(RocketMQConstants.STORE_HOST, messageExt.getStoreHost());
        message.setHeader(RocketMQConstants.MSG_ID, messageExt.getMsgId());
        message.setHeader(RocketMQConstants.COMMIT_LOG_OFFSET, messageExt.getCommitLogOffset());
        message.setHeader(RocketMQConstants.BODY_CRC, messageExt.getBodyCRC());
        message.setHeader(RocketMQConstants.RECONSUME_TIMES, messageExt.getReconsumeTimes());
        message.setHeader(RocketMQConstants.PREPARED_TRANSACTION_OFFSET, messageExt.getPreparedTransactionOffset());
    }
}
