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
package org.apache.camel.component.jms.reply;

import javax.jms.Message;
import javax.jms.Session;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;

/**
 * {@link ReplyHandler} to handle processing replies when using temporary queues.
 *
 * @version 
 */
public class TemporaryQueueReplyHandler implements ReplyHandler {

    // task queue to add the holder so we can process the reply
    protected final ReplyManager replyManager;
    protected final Exchange exchange;
    protected final AsyncCallback callback;
    // remember the original correlation id, in case the server returns back a reply with a messed up correlation id
    protected final String originalCorrelationId;
    protected final String correlationId;
    protected final long timeout;

    public TemporaryQueueReplyHandler(ReplyManager replyManager, Exchange exchange, AsyncCallback callback,
                                      String originalCorrelationId, String correlationId, long timeout) {
        this.replyManager = replyManager;
        this.exchange = exchange;
        this.originalCorrelationId = originalCorrelationId;
        this.correlationId = correlationId;
        this.callback = callback;
        this.timeout = timeout;
    }

    public void onReply(String correlationId, Message reply, Session session) {
        // create holder object with the the reply
        ReplyHolder holder = new ReplyHolder(exchange, callback, originalCorrelationId, correlationId, reply, session);
        // process the reply
        replyManager.processReply(holder);
    }

    public void onTimeout(String correlationId) {
        // create holder object without the reply which means a timeout occurred
        ReplyHolder holder = new ReplyHolder(exchange, callback, originalCorrelationId, correlationId, timeout);
        // process timeout
        replyManager.processReply(holder);
    }
}
