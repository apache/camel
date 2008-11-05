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
package org.apache.camel.component.spring.integration.adapter;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.spring.integration.SpringIntegrationBinding;
import org.apache.camel.component.spring.integration.SpringIntegrationExchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.integration.core.Message;
import org.springframework.integration.core.MessageChannel;
import org.springframework.integration.core.MessageHeaders;
import org.springframework.integration.message.MessageDeliveryException;
import org.springframework.integration.message.MessageHandler;
import org.springframework.integration.message.MessageRejectedException;

/**
 * CamelTargeAdapter will redirect the Spring Integration message to the Camel context.
 * When we inject the camel context into it, we need also specify the Camel endpoint url
 * we will route the Spring Integration message to the Camel context
 * @author Willem Jiang
 *
 * @version $Revision$
 */
public class CamelTargetAdapter extends AbstractCamelAdapter implements MessageHandler {

    private final Log logger = LogFactory.getLog(this.getClass());
    private ProducerTemplate<Exchange> camelTemplate;
    private MessageChannel replyChannel;


    public void setReplyChannel(MessageChannel channel) {
        replyChannel = channel;
    }

    public MessageChannel getReplyChannel() {
        return replyChannel;
    }

    public ProducerTemplate<Exchange> getCamelTemplate() {
        if (camelTemplate == null) {
            CamelContext ctx = getCamelContext();
            if (ctx == null) {
                ctx = new DefaultCamelContext();
            }
            camelTemplate = ctx.createProducerTemplate();
        }
        return camelTemplate;
    }

    public boolean send(Message<?> message) throws MessageRejectedException, MessageDeliveryException {
        ExchangePattern pattern;
        boolean result = false;
        if (isExpectReply()) {
            pattern = ExchangePattern.InOut;
        } else {
            pattern = ExchangePattern.InOnly;
        }
        Exchange inExchange = new SpringIntegrationExchange(getCamelContext(), pattern);
        SpringIntegrationBinding.storeToCamelMessage(message, inExchange.getIn());
        Exchange outExchange = getCamelTemplate().send(getCamelEndpointUri(), inExchange);
        if (outExchange.getFault() != null) {
            result = true;
        }
        Message response = null;
        if (isExpectReply()) {
            //Check the message header for the return address
            response = SpringIntegrationBinding.storeToSpringIntegrationMessage(outExchange.getOut());
            if (replyChannel == null) {
                MessageChannel messageReplyChannel = (MessageChannel) message.getHeaders().get(MessageHeaders.REPLY_CHANNEL);
                if (messageReplyChannel != null) {
                    result = messageReplyChannel.send(response);
                } else {
                    throw new MessageDeliveryException(response, "Can't find reply channel from the CamelTargetAdapter or MessageHeaders");
                }
            } else {
                result = replyChannel.send(response);
            }
        }
        return result;
    }

    public void handleMessage(Message<?> message) {
        send(message);        
    }

}
