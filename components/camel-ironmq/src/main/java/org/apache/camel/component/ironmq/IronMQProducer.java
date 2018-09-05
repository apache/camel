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
package org.apache.camel.component.ironmq;

import io.iron.ironmq.Queue;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The IronMQ producer.
 */
public class IronMQProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(IronMQProducer.class);
    
    private final Queue ironQueue;
    
    public IronMQProducer(IronMQEndpoint endpoint, Queue ironQueue) {
        super(endpoint);
        this.ironQueue = ironQueue;
    }

    public void process(Exchange exchange) throws Exception {
        IronMQConfiguration configuration = getEndpoint().getConfiguration();
        if (IronMQConstants.CLEARQUEUE.equals(exchange.getIn().getHeader(IronMQConstants.OPERATION, String.class))) {
            this.ironQueue.clear();
        } else {
            Object messageId = null;
            Object body = exchange.getIn().getBody();
            if (body instanceof String[]) {
                messageId = this.ironQueue.pushMessages((String[])body, configuration.getVisibilityDelay());
            } else if (body instanceof String) {
                if (configuration.isPreserveHeaders()) {
                    body = GsonUtil.getBodyFromMessage(exchange.getIn());
                }
                messageId = this.ironQueue.push((String)body, configuration.getVisibilityDelay());
            } else {
                throw new InvalidPayloadException(exchange, String.class);
            }
            LOG.trace("Send request [{}] from exchange [{}]...", body, exchange);
            LOG.trace("Received messageId [{}]", messageId);
            Message message = getMessageForResponse(exchange);
            message.setHeader(IronMQConstants.MESSAGE_ID, messageId);
        }
    }

    private Message getMessageForResponse(Exchange exchange) {
        if (exchange.getPattern().isOutCapable()) {
            Message out = exchange.getOut();
            out.copyFrom(exchange.getIn());
            return out;
        }

        return exchange.getIn();
    }

    @Override
    public IronMQEndpoint getEndpoint() {
        return (IronMQEndpoint)super.getEndpoint();
    }

}
