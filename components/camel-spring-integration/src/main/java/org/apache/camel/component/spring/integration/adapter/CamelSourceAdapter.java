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

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.spring.integration.SpringIntegrationBinding;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.Message;
import org.springframework.integration.core.MessageChannel;
import org.springframework.integration.core.MessageHeaders;
import org.springframework.integration.message.MessageHandler;

/**
 * A CamelContext will be injected into CameSourceAdapter which will
 * let Spring Integration channel talk to the CamelContext certain endpoint
 *
 * @version $Revision$
 */
public class CamelSourceAdapter extends AbstractCamelAdapter implements InitializingBean, DisposableBean {
    private static final Log LOG = LogFactory.getLog(CamelSourceAdapter.class);

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
        public void process(final Exchange exchange) throws Exception {
            org.springframework.integration.core.Message request = SpringIntegrationBinding.createSpringIntegrationMessage(exchange);

            if (exchange.getPattern().isOutCapable()) {
                exchange.getIn().getHeaders().put(MessageHeaders.REPLY_CHANNEL , replyChannel);

                // we want to do in-out so the inputChannel is mandatory (used to receive reply from spring integration)
                if (replyChannel == null) {
                    throw new IllegalArgumentException("ReplyChannel has not been configured on: " + this);
                }

                replyChannel.subscribe(new MessageHandler() {
                    public void handleMessage(Message<?> message) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Received " + message + " from ReplyChannel: " + replyChannel);
                        }
                        //TODO set the corralationID
                        SpringIntegrationBinding.storeToCamelMessage(message, exchange.getOut());
                    }
                });
            }
                 
            requestChannel.send(request);
        }
    }

    public final void afterPropertiesSet() throws Exception {
        if (initialized.compareAndSet(false, true)) {
            initialize();
        }
    }

    public void destroy() throws Exception {
        if (consumer != null) {
            consumer.stop();
        }
    }

    protected void initialize() throws Exception {
        // start the service here
        camelEndpoint = getCamelContext().getEndpoint(getCamelEndpointUri());
        consumer = camelEndpoint.createConsumer(new ConsumerProcessor());
        consumer.start();
    }

}
