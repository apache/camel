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

package org.apache.camel.component.rocketmq.reply;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RocketMQReplyHandler implements ReplyHandler {

    protected static final Logger LOG = LoggerFactory.getLogger(RocketMQReplyHandler.class);

    protected final ReplyManager replyManager;
    protected final Exchange exchange;
    protected final AsyncCallback callback;
    protected final String messageKey;
    protected final long timeout;

    public RocketMQReplyHandler(ReplyManager replyManager, Exchange exchange, AsyncCallback callback, String messageKey,
                                long timeout) {
        this.replyManager = replyManager;
        this.exchange = exchange;
        this.callback = callback;
        this.messageKey = messageKey;
        this.timeout = timeout;
    }

    @Override
    public void onReply(String messageKey, MessageExt messageExt) {
        LOG.debug("onReply with messageKey: {}", messageKey);
        ReplyHolder holder = new ReplyHolder(exchange, callback, messageKey, messageExt);
        replyManager.processReply(holder);
    }

    @Override
    public void onTimeout(String messageKey) {
        LOG.debug("onTimeout with messageKey: {}", messageKey);
        ReplyHolder holder = new ReplyHolder(exchange, callback, messageKey, timeout);
        replyManager.processReply(holder);
    }
}
