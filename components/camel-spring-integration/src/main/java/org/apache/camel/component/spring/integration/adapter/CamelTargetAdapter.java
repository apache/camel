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
package org.apache.camel.component.spring.integration.adapter;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.spring.integration.SpringIntegrationBinding;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessageHeaders;

/**
 * CamelTargetAdapter will redirect the Spring Integration message to the Camel context.
 * When we inject the camel context into it, we need also specify the Camel endpoint url
 * we will route the Spring Integration message to the Camel context
 */
public class CamelTargetAdapter extends AbstractCamelAdapter implements MessageHandler {
    private ProducerTemplate camelTemplate;
    private MessageChannel replyChannel;

    public void setReplyChannel(MessageChannel channel) {
        this.replyChannel = channel;
    }

    public MessageChannel getReplyChannel() {
        return replyChannel;
    }

    public ProducerTemplate getCamelTemplate() throws Exception {
        if (camelTemplate == null) {
            CamelContext ctx = getCamelContext();
            if (ctx == null) {
                // TODO: This doesnt look good to create a new CamelContext out of the blue
                ctx = new DefaultCamelContext();
            }
            camelTemplate = ctx.createProducerTemplate();
        }
        return camelTemplate;
    }

    public boolean send(Message<?> message) throws Exception {
        boolean result = false;

        ExchangePattern pattern;
        if (isExpectReply()) {
            pattern = ExchangePattern.InOut;
        } else {
            pattern = ExchangePattern.InOnly;
        }

        Exchange inExchange = new DefaultExchange(getCamelContext(), pattern);
        SpringIntegrationBinding.storeToCamelMessage(message, inExchange.getIn());
        Exchange outExchange = getCamelTemplate().send(getCamelEndpointUri(), inExchange);

        Message<?> response;
        if (isExpectReply()) {
            //Check the message header for the return address
            response = SpringIntegrationBinding.storeToSpringIntegrationMessage(outExchange.getOut());
            if (replyChannel == null) {
                MessageChannel messageReplyChannel = (MessageChannel) message.getHeaders().get(MessageHeaders.REPLY_CHANNEL);
                if (messageReplyChannel != null) {
                    result = messageReplyChannel.send(response);
                } else {
                    throw new MessageDeliveryException(response, "Cannot resolve ReplyChannel from message: " + message);
                }
            } else {
                result = replyChannel.send(response);
            }
        }

        return result;
    }

    @Override
    public void handleMessage(Message<?> message) throws MessageDeliveryException {
        try {
            send(message);
        } catch (Exception e) {
            throw new MessageDeliveryException(message, "Cannot send message", e);
        }
    }

}
