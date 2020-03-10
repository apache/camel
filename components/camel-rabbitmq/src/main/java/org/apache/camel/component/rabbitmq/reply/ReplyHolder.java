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
package org.apache.camel.component.rabbitmq.reply;

import com.rabbitmq.client.AMQP;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;

/**
 * Holder which contains the {@link Exchange} and
 * {@link org.apache.camel.AsyncCallback} to be used when the reply arrives, so
 * we can set the reply on the {@link Exchange} and continue routing using the
 * callback.
 */
public class ReplyHolder {

    private final Exchange exchange;
    private final AsyncCallback callback;
    private final byte[] message;
    private final String originalCorrelationId;
    private final String correlationId;
    private long timeout;
    private AMQP.BasicProperties properties;

    /**
     * Constructor to use when a reply message was received
     */
    public ReplyHolder(Exchange exchange, AsyncCallback callback, String originalCorrelationId, String correlationId, AMQP.BasicProperties properties, byte[] message) {
        this.exchange = exchange;
        this.callback = callback;
        this.originalCorrelationId = originalCorrelationId;
        this.correlationId = correlationId;
        this.properties = properties;
        this.message = message;
    }

    /**
     * Constructor to use when a timeout occurred
     */
    public ReplyHolder(Exchange exchange, AsyncCallback callback, String originalCorrelationId, String correlationId, long timeout) {
        this(exchange, callback, originalCorrelationId, correlationId, null, null);
        this.timeout = timeout;
    }

    public Exchange getExchange() {
        return exchange;
    }

    public AsyncCallback getCallback() {
        return callback;
    }

    /**
     * Gets the original correlation id, if one was set when sending the
     * message.
     * <p/>
     * Some JMS brokers will mess with the correlation id and send back a
     * different/empty correlation id. So we need to remember it so we can
     * restore the correlation id.
     */
    public String getOriginalCorrelationId() {
        return originalCorrelationId;
    }

    /**
     * Gets the correlation id
     *
     * @see #getOriginalCorrelationId()
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Gets the received message
     *
     * @return the received message, or <tt>null</tt> if timeout occurred and no
     *         message has been received
     * @see #isTimeout()
     */
    public byte[] getMessage() {
        return message;
    }

    /**
     * Whether timeout triggered or not.
     * <p/>
     * A timeout is triggered if <tt>requestTimeout</tt> option has been
     * configured, and a reply message has <b>not</b> been received within that
     * time frame.
     */
    public boolean isTimeout() {
        return message == null;
    }

    /**
     * The timeout value
     */
    public long getRequestTimeout() {
        return timeout;
    }

    /**
     * The message properties
     */
    public AMQP.BasicProperties getProperties() {
        return properties;
    }
}
