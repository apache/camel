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

import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.springframework.integration.channel.AbstractPollableChannel;
import org.springframework.integration.channel.BeanFactoryChannelResolver;
import org.springframework.integration.channel.ChannelResolver;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.Message;
import org.springframework.integration.core.MessageChannel;
import org.springframework.integration.message.MessageHandler;

/**
 * A consumer of exchanges for the Spring Integration
 * Please specify the inputChannel in the endpoint url for this consumer.
 * If the message pattern is inOut, the outputChannel property
 * should be set for the outgoing message.
 *
 * @version $Revision$
 */
public class SpringIntegrationConsumer  extends DefaultConsumer<SpringIntegrationExchange> implements MessageHandler {
    private SpringCamelContext context;
    private DirectChannel inputChannel;
    private MessageChannel outputChannel;
    private String inputChannelName;
    private ChannelResolver channelResolver;
    private SpringIntegrationEndpoint endpoint;

    public SpringIntegrationConsumer(SpringIntegrationEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        context = (SpringCamelContext) endpoint.getCamelContext();
        if (context != null && endpoint.getMessageChannel() == null) {
            channelResolver = new BeanFactoryChannelResolver(context.getApplicationContext());
            inputChannelName = endpoint.getDefaultChannel();
            if (ObjectHelper.isNullOrBlank(inputChannelName)) {
                inputChannelName = endpoint.getInputChannel();
            }
            if (!ObjectHelper.isNullOrBlank(inputChannelName)) {
                inputChannel = (DirectChannel) channelResolver.resolveChannelName(inputChannelName);
                ObjectHelper.notNull(inputChannel, "The inputChannel with the name [" + inputChannelName + "]");
            } else {
                throw new RuntimeCamelException("Can't find the right inputChannelName, please check your configuration.");
            }
        } else {
            if (endpoint.getMessageChannel() != null) {
                inputChannel = (DirectChannel)endpoint.getMessageChannel();
            } else {
                throw new RuntimeCamelException("Can't find the right message channel, please check your configuration.");
            }
        }
        if (endpoint.isInOut()) {
            endpoint.setExchangePattern(ExchangePattern.InOut);
        }

    }
    
    protected void doStop() throws Exception {
        inputChannel.unsubscribe(this);
        super.doStop();
    }

    protected void doStart() throws Exception {
        super.doStart();
        inputChannel.subscribe(this);
    }
    
    public void handleMessage(org.springframework.integration.core.Message<?> siInMessage) {        
        SpringIntegrationExchange  exchange = getEndpoint().createExchange();
        exchange.setIn(new SpringIntegrationMessage(siInMessage));
        try {
            getProcessor().process(exchange);
        } catch (Exception e) {
            //TODO need to find a way to deal with this exception
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
        if (endpoint.isInOut()) {
            // get the output channel from message header
            Object returnAddress = siInMessage.getHeaders().getReplyChannel();
            MessageChannel reply = null;

            if (returnAddress != null) {
                if (returnAddress instanceof String) {
                    reply = (MessageChannel)context.getApplicationContext().getBean((String)returnAddress);
                } else if (returnAddress instanceof MessageChannel) {
                    reply = (MessageChannel) returnAddress;
                }
            } else {
                if (outputChannel != null) {
                    // using the outputChannel
                    reply = outputChannel;
                } else {
                    if (ObjectHelper.isNullOrBlank(endpoint.getOutputChannel())) {
                        outputChannel = (MessageChannel) channelResolver.resolveChannelName(endpoint.getOutputChannel());
                        ObjectHelper.notNull(inputChannel, "The outputChannel with the name [" + endpoint.getOutputChannel() + "]");
                        reply = outputChannel;
                    } else {
                        throw new RuntimeCamelException("Can't find the right outputChannelName");
                    }
                }
            }
            // put the message back the outputChannel if we need
            org.springframework.integration.core.Message siOutMessage =
                SpringIntegrationBinding.storeToSpringIntegrationMessage(exchange.getOut());
            reply.send(siOutMessage);
        }        
    }   

}
