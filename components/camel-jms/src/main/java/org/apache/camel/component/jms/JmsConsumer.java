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
package org.apache.camel.component.jms;

import javax.jms.MessageListener;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.springframework.jms.listener.AbstractMessageListenerContainer;

/**
 * A {@link Consumer} which uses Spring's {@link AbstractMessageListenerContainer} implementations to consume JMS messages
 *
 * @version $Revision$
 */
public class JmsConsumer extends DefaultConsumer<JmsExchange> {
    private final AbstractMessageListenerContainer listenerContainer;
    private EndpointMessageListener messageListener;

    public JmsConsumer(JmsEndpoint endpoint, Processor processor, AbstractMessageListenerContainer listenerContainer) {
        super(endpoint, processor);
        this.listenerContainer = listenerContainer;

        createMessageListener(endpoint, processor);
        this.listenerContainer.setMessageListener(messageListener);
    }

    public AbstractMessageListenerContainer getListenerContainer() {
        return listenerContainer;
    }

    public EndpointMessageListener getEndpointMessageListener() {
        return messageListener;
    }
    
    protected void createMessageListener(JmsEndpoint endpoint, Processor processor) {
        messageListener = new EndpointMessageListener(endpoint, processor);
        messageListener.setBinding(endpoint.getBinding());
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        listenerContainer.afterPropertiesSet();
        listenerContainer.start();
    }

    @Override
    protected void doStop() throws Exception {
        listenerContainer.stop();
        listenerContainer.destroy();
        super.doStop();
    }
}
