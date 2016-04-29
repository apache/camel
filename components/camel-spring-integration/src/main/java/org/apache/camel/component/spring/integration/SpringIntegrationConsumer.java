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
package org.apache.camel.component.spring.integration;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.util.ObjectHelper;
import org.springframework.integration.support.channel.BeanFactoryChannelResolver;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.core.DestinationResolver;

/**
 * A consumer of exchanges for the Spring Integration
 * Please specify the inputChannel in the endpoint url for this consumer.
 * If the message pattern is inOut, the outputChannel property
 * should be set for the outgoing message.
 *
 * @version 
 */
public class SpringIntegrationConsumer  extends DefaultConsumer implements MessageHandler {
    private final SpringCamelContext context;
    private final DestinationResolver<MessageChannel> destinationResolver;
    private SubscribableChannel inputChannel;
    private MessageChannel outputChannel;

    public SpringIntegrationConsumer(SpringIntegrationEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.context = (SpringCamelContext) endpoint.getCamelContext();
        this.destinationResolver = new BeanFactoryChannelResolver(context.getApplicationContext());
    }

    @Override
    public SpringIntegrationEndpoint getEndpoint() {
        return (SpringIntegrationEndpoint) super.getEndpoint();
    }

    protected void doStop() throws Exception {
        inputChannel.unsubscribe(this);
        super.doStop();
    }

    protected void doStart() throws Exception {
        super.doStart();

        if (getEndpoint().getMessageChannel() == null) {
            String inputChannelName = getEndpoint().getDefaultChannel();
            if (ObjectHelper.isEmpty(inputChannelName)) {
                inputChannelName = getEndpoint().getInputChannel();
            }

            ObjectHelper.notEmpty(inputChannelName, "inputChannelName", getEndpoint());
            inputChannel = (SubscribableChannel) destinationResolver.resolveDestination(inputChannelName);
        } else {
            inputChannel = (SubscribableChannel) getEndpoint().getMessageChannel();
        }

        if (inputChannel == null) {
            throw new IllegalArgumentException("Cannot resolve InputChannel on " + getEndpoint());
        }

        // if we do in-out we need to setup the input channel as well
        if (getEndpoint().isInOut()) {
            // we need to setup right outputChannel for further processing
            ObjectHelper.notEmpty(getEndpoint().getOutputChannel(), "OutputChannel", getEndpoint());
            outputChannel = destinationResolver.resolveDestination(getEndpoint().getOutputChannel());

            if (outputChannel == null) {
                throw new IllegalArgumentException("Cannot resolve OutputChannel on " + getEndpoint());
            }
        }

        inputChannel.subscribe(this);
    }

    public void handleMessage(org.springframework.messaging.Message<?> siInMessage) {
        // we received a message from spring integration
        // wrap that in a Camel Exchange and process it
        Exchange exchange = getEndpoint().createExchange(getEndpoint().isInOut() ? ExchangePattern.InOut : ExchangePattern.InOnly);
        exchange.setIn(new SpringIntegrationMessage(siInMessage));

        // process the exchange
        try {
            getProcessor().process(exchange);
        } catch (Exception e) {
            getExceptionHandler().handleException("Error processing exchange", exchange, e);
            return;
        }

        // reply logic
        if (getEndpoint().isInOut()) {
            MessageChannel reply = null;

            // get the output channel from message header
            Object returnAddress = siInMessage.getHeaders().getReplyChannel();
            if (returnAddress != null) {
                if (returnAddress instanceof String) {
                    reply = context.getApplicationContext().getBean((String)returnAddress, MessageChannel.class);
                } else if (returnAddress instanceof MessageChannel) {
                    reply = (MessageChannel) returnAddress;
                }
            } else {
                reply = outputChannel;

                // we want to do in-out so the inputChannel is mandatory (used to receive reply from spring integration)
                if (reply == null) {
                    throw new IllegalArgumentException("OutputChannel has not been configured on " + getEndpoint());
                }
            }

            if (reply == null) {
                throw new IllegalArgumentException("Cannot resolve ReplyChannel from message: " + siInMessage);
            }

            // put the message back the outputChannel if we need
            org.springframework.messaging.Message<?> siOutMessage =
                SpringIntegrationBinding.storeToSpringIntegrationMessage(exchange.getOut());

            // send the message to spring integration
            log.debug("Sending {} to ReplyChannel: {}", siOutMessage, reply);
            reply.send(siOutMessage);
        }        
    }   

}
