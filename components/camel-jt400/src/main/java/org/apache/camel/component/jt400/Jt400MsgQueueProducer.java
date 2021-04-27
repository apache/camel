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
package org.apache.camel.component.jt400;

import com.ibm.as400.access.MessageQueue;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

/**
 * {@link Producer} to send data to an IBM i message queue.
 */
public class Jt400MsgQueueProducer extends DefaultProducer {

    private final Jt400Endpoint endpoint;

    /**
     * Performs the lifecycle logic of this producer.
     */
    protected Jt400MsgQueueProducer(Jt400Endpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    /**
     * Sends the {@link Exchange}'s in body to the message queue as an informational message. Data will be sent as a
     * <code>String</code>.
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        try (Jt400MsgQueueService queueService = new Jt400MsgQueueService(endpoint)) {
            queueService.start();
            process(queueService.getMsgQueue(), exchange);
        }
    }

    private void process(MessageQueue queue, Exchange exchange) throws Exception {
        String msgText = exchange.getIn().getBody(String.class);
        byte[] messageKey = exchange.getIn().getHeader(Jt400Constants.MESSAGE_REPLYTO_KEY, byte[].class);
        if (ObjectHelper.isNotEmpty(messageKey) && ObjectHelper.isNotEmpty(msgText)) {
            queue.reply(messageKey, msgText);
        } else {
            queue.sendInformational(msgText);
        }
    }

}
