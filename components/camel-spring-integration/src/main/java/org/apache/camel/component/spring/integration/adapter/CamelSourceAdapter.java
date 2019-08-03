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

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.spring.integration.SpringIntegrationBinding;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessageHeaders;

/**
 * A CamelContext will be injected into CameSourceAdapter which will
 * let Spring Integration channel talk to the CamelContext certain endpoint
 */
public class CamelSourceAdapter extends AbstractCamelAdapter implements InitializingBean, DisposableBean {
    private static final Logger LOG = LoggerFactory.getLogger(CamelSourceAdapter.class);

    private Consumer consumer;
    private Endpoint camelEndpoint;
    private MessageChannel requestChannel;
    private DirectChannel replyChannel;
    private final AtomicBoolean initialized = new AtomicBoolean();

    public void setRequestChannel(MessageChannel channel) {
        requestChannel = channel;        
    }

    public MessageChannel getChannel() {
        return requestChannel;
    }

    public void setReplyChannel(DirectChannel channel) {        
        replyChannel = channel;
    }

    protected class ConsumerProcessor implements Processor {
        @Override
        public void process(final Exchange exchange) throws Exception {
            org.springframework.messaging.Message<?> request = SpringIntegrationBinding.createSpringIntegrationMessage(exchange);

            if (exchange.getPattern().isOutCapable()) {
                exchange.getIn().getHeaders().put(MessageHeaders.REPLY_CHANNEL, replyChannel);

                // we want to do in-out so the inputChannel is mandatory (used to receive reply from spring integration)
                if (replyChannel == null) {
                    throw new IllegalArgumentException("ReplyChannel has not been configured on: " + this);
                }

                replyChannel.subscribe(new MessageHandler() {
                    public void handleMessage(Message<?> message) {
                        LOG.debug("Received {} from ReplyChannel: {}", message, replyChannel);
                        //TODO set the correlationID
                        SpringIntegrationBinding.storeToCamelMessage(message, exchange.getOut());
                    }
                });
            }
                 
            requestChannel.send(request);
        }
    }

    @Override
    public final void afterPropertiesSet() throws Exception {
        if (initialized.compareAndSet(false, true)) {
            initialize();
        }
    }

    @Override
    public void destroy() throws Exception {
        if (consumer != null) {
            ServiceHelper.stopAndShutdownService(consumer);
        }
    }

    protected void initialize() throws Exception {
        // start the service here
        camelEndpoint = getCamelContext().getEndpoint(getCamelEndpointUri());
        consumer = camelEndpoint.createConsumer(new ConsumerProcessor());
        ServiceHelper.startService(consumer);
    }

}
