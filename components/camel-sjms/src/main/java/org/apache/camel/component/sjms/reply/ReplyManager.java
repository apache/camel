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
package org.apache.camel.component.sjms.reply;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import jakarta.jms.Destination;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.sjms.SessionMessageListener;
import org.apache.camel.component.sjms.SjmsEndpoint;

/**
 * The {@link ReplyManager} is responsible for handling
 * <a href="http://camel.apache.org/request-reply.html">request-reply</a> over JMS.
 */
public interface ReplyManager extends SessionMessageListener {

    /**
     * Sets the belonging {@link org.apache.camel.component.sjms.SjmsEndpoint}.
     */
    void setEndpoint(SjmsEndpoint endpoint);

    /**
     * Sets the reply to queue the manager should listen for replies.
     * <p/>
     * The queue is either a temporary or a persistent queue.
     */
    void setReplyTo(Destination replyTo);

    /**
     * Sets the scheduled thread pool to use when checking for timeouts (no reply received within a given time period)
     */
    void setScheduledExecutorService(ScheduledExecutorService executorService);

    /**
     * Sets the thread pool to use for continue routing {@link Exchange} when a timeout was triggered when doing
     * request/reply over JMS.
     */
    void setOnTimeoutExecutorService(ExecutorService executorService);

    /**
     * Gets the reply to queue being used
     */
    Destination getReplyTo();

    /**
     * Register a reply
     *
     * @param  replyManager          the reply manager being used
     * @param  exchange              the exchange
     * @param  callback              the callback
     * @param  originalCorrelationId an optional original correlation id
     * @param  correlationId         the correlation id to expect being used
     * @param  requestTimeout        the timeout
     * @return                       the correlation id used
     */
    String registerReply(
            ReplyManager replyManager, Exchange exchange, AsyncCallback callback,
            String originalCorrelationId, String correlationId, long requestTimeout);

    /**
     * Updates the correlation id to the new correlation id.
     * <p/>
     * This is only used when <tt>useMessageIDasCorrelationID</tt> option is used, which means a provisional correlation
     * id is first used, then after the message has been sent, the real correlation id is known. This allows us then to
     * update the internal mapping to expect the real correlation id.
     *
     * @param correlationId    the provisional correlation id
     * @param newCorrelationId the real correlation id
     * @param requestTimeout   the timeout
     */
    void updateCorrelationId(String correlationId, String newCorrelationId, long requestTimeout);

    /**
     * Cancels a pending reply correlation, so the request timeout does not complete the exchange.
     * <p/>
     * This is used when the JMS send fails after the reply has been registered. Whoever removes the correlation owns
     * the completion of the exchange. When this method returns <tt>false</tt>, the request timeout or the reply has
     * already removed the correlation (for example while a slow send was still running), and it completes the exchange:
     * the caller must then not complete the exchange as well.
     * <p/>
     * The default implementation does not cancel anything and returns <tt>true</tt>, which keeps the previous behaviour
     * for a custom implementation. {@link ReplyManagerSupport} overrides it.
     *
     * @param  correlationId the correlation id to cancel
     * @return               <tt>true</tt> if the correlation was pending and has been cancelled, <tt>false</tt> if it
     *                       was not pending (anymore)
     */
    default boolean cancelCorrelationId(String correlationId) {
        return true;
    }

    /**
     * Process the reply
     *
     * @param holder containing needed data to process the reply and continue routing
     */
    void processReply(ReplyHolder holder);
}
