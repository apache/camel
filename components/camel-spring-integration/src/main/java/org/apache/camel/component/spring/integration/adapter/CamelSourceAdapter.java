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

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.spring.integration.SpringIntegrationBinding;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.integration.ConfigurationException;
import org.springframework.integration.bus.MessageBus;
import org.springframework.integration.bus.MessageBusAware;
import org.springframework.integration.channel.MessageChannel;
import org.springframework.integration.gateway.RequestReplyTemplate;
import org.springframework.integration.message.Message;

/**
 * A CamelContext will be injected into CameSourceAdapter which will
 * let Spring Integration channel talk to the CamelContext certain endpoint
 *
 * @author Willem Jiang
 *
 * @version $Revision$
 */
public class CamelSourceAdapter extends AbstractCamelAdapter implements InitializingBean, MessageBusAware {
    protected final Object lifecycleMonitor = new Object();
    private final Log logger = LogFactory.getLog(this.getClass());
    private Consumer consumer;
    private Endpoint camelEndpoint;
    private MessageChannel requestChannel;
    private RequestReplyTemplate requestReplyTemplate = new RequestReplyTemplate();

    private volatile boolean initialized;

    public void setRequestChannel(MessageChannel channel) {
        requestChannel = channel;
        requestReplyTemplate.setRequestChannel(requestChannel);
    }

    public MessageChannel getChannel() {
        return requestChannel;
    }

    public void setReplyChannel(MessageChannel channel) {
        requestReplyTemplate.setReplyChannel(channel);
    }

    public void setRequestTimeout(long requestTimeout) {
        this.requestReplyTemplate.setRequestTimeout(requestTimeout);
    }

    public void setReplyTimeout(long replyTimeout) {
        this.requestReplyTemplate.setReplyTimeout(replyTimeout);
    }

    private void incoming(Exchange exchange) {
        org.springframework.integration.message.Message request =
            SpringIntegrationBinding.createSpringIntegrationMessage(exchange);

        org.springframework.integration.message.Message response = handle(request);
        if (response != null) {
            // TODO How to deal with the fault message
            SpringIntegrationBinding.storeToCamelMessage(response, exchange.getOut());
        }
    }

    protected class ConsumerProcessor implements Processor {
        public void process(Exchange exchange) {
            try {
                incoming(exchange);
            } catch (Throwable ex) {
                ex.printStackTrace();
                logger.warn("Failed to process incoming message : " + ex);
                //TODO Maybe we should set the exception as the fault message
            }
        }

    }

    public final void afterPropertiesSet() throws Exception {
        synchronized (this.lifecycleMonitor) {
            if (this.initialized) {
                return;
            }
        }
        this.initialize();
        this.initialized = true;
    }

    protected void initialize() throws Exception {
        // start the service here
        camelEndpoint = getCamelContext().getEndpoint(getCamelEndpointUri());
        consumer = camelEndpoint.createConsumer(new ConsumerProcessor());
        consumer.start();
    }

    public final Message<?> handle(Message<?> message) {
        if (!this.initialized) {
            try {
                this.afterPropertiesSet();
            } catch (Exception e) {
                throw new ConfigurationException("unable to initialize " + this.getClass().getName(), e);
            }
        }
        if (!isExpectReply()) {
            boolean sent = this.requestReplyTemplate.send(message);
            if (!sent && logger.isWarnEnabled()) {
                logger.warn("failed to send message to channel within timeout");
            }
            return null;
        }
        return this.requestReplyTemplate.request(message);
    }

    public void setMessageBus(MessageBus bus) {
        requestReplyTemplate.setMessageBus(bus);
    }



}
