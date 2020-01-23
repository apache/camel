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
package org.apache.camel.component.spring.integration;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.support.channel.BeanFactoryChannelResolver;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.core.DestinationResolver;

/**
 * A producer of exchanges for the Spring Integration
 * Please specify the outputChannel in the endpoint url for this producer.
 * If the message pattern is inOut, the inputChannel property
 * should be set for receiving the response message.
 */
public class SpringIntegrationProducer extends DefaultProducer implements Processor {

    private static final Logger LOG = LoggerFactory.getLogger(SpringIntegrationProducer.class);

    private final DestinationResolver<MessageChannel> destinationResolver;
    private DirectChannel inputChannel;
    private MessageChannel outputChannel;

    public SpringIntegrationProducer(SpringCamelContext context, SpringIntegrationEndpoint endpoint) {
        super(endpoint);
        this.destinationResolver = new BeanFactoryChannelResolver(context.getApplicationContext());
    }

    @Override
    public SpringIntegrationEndpoint getEndpoint() {
        return (SpringIntegrationEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (getEndpoint().getMessageChannel() == null) {
            String outputChannelName = getEndpoint().getDefaultChannel();
            if (ObjectHelper.isEmpty(outputChannelName)) {
                outputChannelName = getEndpoint().getInputChannel();
            }

            StringHelper.notEmpty(outputChannelName, "OutputChannelName", getEndpoint());
            outputChannel = destinationResolver.resolveDestination(outputChannelName);
        } else {
            outputChannel = getEndpoint().getMessageChannel();
        }

        if (outputChannel == null) {
            throw new IllegalArgumentException("Cannot resolve OutputChannel on " + getEndpoint());
        }

        // if we do in-out we need to setup the input channel as well
        if (getEndpoint().isInOut()) {
            // we need to setup right inputChannel for further processing
            StringHelper.notEmpty(getEndpoint().getInputChannel(), "InputChannel", getEndpoint());
            inputChannel = (DirectChannel)destinationResolver.resolveDestination(getEndpoint().getInputChannel());

            if (inputChannel == null) {
                throw new IllegalArgumentException("Cannot resolve InputChannel on " + getEndpoint());
            }
        }
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        if (exchange.getPattern().isOutCapable()) {

            // we want to do in-out so the inputChannel is mandatory (used to receive reply from spring integration)
            if (inputChannel == null) {
                throw new IllegalArgumentException("InputChannel has not been configured on " + getEndpoint());
            }
            exchange.getIn().getHeaders().put(MessageHeaders.REPLY_CHANNEL, inputChannel);

            // subscribe so we can receive the reply from spring integration
            inputChannel.subscribe(new MessageHandler() {
                public void handleMessage(Message<?> message) {
                    LOG.debug("Received {} from InputChannel: {}", message, inputChannel);
                    SpringIntegrationBinding.storeToCamelMessage(message, exchange.getOut());
                }
            });
        }
        org.springframework.messaging.Message<?> siOutmessage = SpringIntegrationBinding.createSpringIntegrationMessage(exchange);

        // send the message to spring integration
        LOG.debug("Sending {} to OutputChannel: {}", siOutmessage, outputChannel);
        outputChannel.send(siOutmessage);
    }

}
