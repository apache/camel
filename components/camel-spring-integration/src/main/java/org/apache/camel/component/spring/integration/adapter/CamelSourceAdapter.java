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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.spring.integration.SpringIntegrationBinding;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.PollableChannel;
import org.springframework.integration.core.Message;
import org.springframework.integration.core.MessageChannel;
import org.springframework.integration.core.MessageHeaders;
import org.springframework.integration.gateway.SimpleMessagingGateway;
import org.springframework.integration.message.MessageHandler;
import org.springframework.integration.transformer.Transformer;

/**
 * A CamelContext will be injected into CameSourceAdapter which will
 * let Spring Integration channel talk to the CamelContext certain endpoint
 *
 * @author Willem Jiang
 *
 * @version $Revision$
 */
public class CamelSourceAdapter extends AbstractCamelAdapter implements InitializingBean {
    protected final Object lifecycleMonitor = new Object();
    private final Log logger = LogFactory.getLog(this.getClass());
    private Consumer consumer;
    private Endpoint camelEndpoint;
    private MessageChannel requestChannel;
    private DirectChannel replyChannel;
    
    private volatile boolean initialized;

    public void setRequestChannel(MessageChannel channel) {
        requestChannel = channel;        
    }

    public MessageChannel getChannel() {
        return requestChannel;
    }

    public void setReplyChannel(DirectChannel channel) {        
        replyChannel = channel;
    }

    protected class ConsumerProcessor implements AsyncProcessor {
        public void process(Exchange exchange) throws Exception {
            AsyncProcessorHelper.process(this, exchange);      
        }

        public boolean process(final Exchange exchange, final AsyncCallback callback) {
            org.springframework.integration.core.Message request =
                SpringIntegrationBinding.createSpringIntegrationMessage(exchange);
            Map<String, Object> headers = new HashMap<String, Object>();
            if (exchange.getPattern().isOutCapable()) {
                headers.put(MessageHeaders.REPLY_CHANNEL , replyChannel);
                replyChannel.subscribe(new MessageHandler() {                
                    public void handleMessage(Message<?> message) {
                        //TODO set the corralationID
                        SpringIntegrationBinding.storeToCamelMessage(message, exchange.getOut());
                        callback.done(true);
                    }
                });
            }
                 
            requestChannel.send(request);
            
            if (!exchange.getPattern().isOutCapable()) {
                callback.done(true);
            }            
            return true;
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
}
