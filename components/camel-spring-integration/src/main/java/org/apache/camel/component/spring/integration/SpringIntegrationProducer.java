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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.integration.channel.AbstractPollableChannel;
import org.springframework.integration.channel.ChannelRegistry;
import org.springframework.integration.channel.MessageChannel;
import org.springframework.integration.config.MessageBusParser;
import org.springframework.integration.message.MessageHeaders;

/**
 * A producer of exchanges for the Spring Integration
 * Please specify the outputChannel in the endpoint url for this producer.
 * If the message pattern is inOut, the inputChannel property
 * should be set for receiving the response message.
 * @version $Revision$
 */
public class SpringIntegrationProducer extends DefaultProducer<SpringIntegrationExchange> {
    private static final transient Log LOG = LogFactory.getLog(SpringIntegrationProducer.class);
    private SpringCamelContext context;
    private AbstractPollableChannel inputChannel;
    private MessageChannel outputChannel;
    private String outputChannelName;
    private ChannelRegistry channelRegistry;
    private SpringIntegrationEndpoint endpoint;

    public SpringIntegrationProducer(SpringIntegrationEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        context = (SpringCamelContext) endpoint.getCamelContext();
        if (context != null && endpoint.getMessageChannel() == null) {
            outputChannelName = endpoint.getDefaultChannel();
            channelRegistry = (ChannelRegistry) context.getApplicationContext().getBean(MessageBusParser.MESSAGE_BUS_BEAN_NAME);
            if (ObjectHelper.isNullOrBlank(outputChannelName)) {
                outputChannelName = endpoint.getInputChannel();
            }
            if (ObjectHelper.isNullOrBlank(outputChannelName)) {
                throw new RuntimeCamelException("Can't find the right outputChannelName,"
                                                + "please check the endpoint uri outputChannel part!");
            } else {
                outputChannel = (AbstractPollableChannel) channelRegistry.lookupChannel(outputChannelName);
            }
        } else {
            if (endpoint.getMessageChannel() != null) {
                outputChannel = endpoint.getMessageChannel();
            } else {
                throw new RuntimeCamelException("Can't find the right message channel, please check your configuration.");
            }
        }
        if (endpoint.isInOut()) {
            endpoint.setExchangePattern(ExchangePattern.InOut);
            // we need to setup right inputChannel for further processing
            if (ObjectHelper.isNullOrBlank(endpoint.getInputChannel())) {
                throw new RuntimeCamelException("Can't find the right inputChannel, "
                                                + "please check the endpoint uri inputChannel part!");
            } else {
                inputChannel = (AbstractPollableChannel) channelRegistry.lookupChannel(endpoint.getInputChannel());
            }
        }
    }

    public void process(Exchange exchange) throws Exception {
        Map<String, Object> headers = new HashMap<String, Object>();
        if (exchange.getPattern().isInCapable()) {
            headers.put(MessageHeaders.RETURN_ADDRESS , inputChannel);
        }
        org.springframework.integration.message.Message siOutmessage = SpringIntegrationBinding.createSpringIntegrationMessage(exchange);

        outputChannel.send(siOutmessage);
        if (exchange.getPattern().isInCapable()) {
            org.springframework.integration.message.Message siInMessage = inputChannel.receive();
            SpringIntegrationBinding.storeToCamelMessage(siInMessage, exchange.getOut());
        }

    }


}
