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
package org.apache.camel.component.cometd;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.util.ExchangeHelper;
import org.cometd.bayeux.server.ServerChannel;
import org.cometd.bayeux.server.ServerMessage;
import org.cometd.bayeux.server.ServerSession;
import org.cometd.server.AbstractService;
import org.cometd.server.BayeuxServerImpl;

/**
 * A Consumer for receiving messages using Cometd and Bayeux protocol.
 */
public class CometdConsumer extends DefaultConsumer implements CometdProducerConsumer {

    private BayeuxServerImpl bayeux;
    private final CometdEndpoint endpoint;
    private ConsumerService service;

    public CometdConsumer(CometdEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    public void start() throws Exception {
        super.start();
        // must connect first
        endpoint.connect(this);
        // should probably look into synchronization for this.
        if (service == null) {
            service = new ConsumerService(endpoint.getPath(), bayeux, this);
        }
    }

    @Override
    public void stop() throws Exception {
        endpoint.disconnect(this);
        super.stop();
    }

    public void setBayeux(BayeuxServerImpl bayeux) {
        this.bayeux = bayeux;
    }

    public CometdEndpoint getEndpoint() {
        return endpoint;
    }
    
    public ConsumerService getConsumerService() {
        return service;
    }

    public static class ConsumerService extends AbstractService {

        private final CometdEndpoint endpoint;
        private final CometdConsumer consumer;
        private final CometdBinding binding;
        private final String channelName;

        public ConsumerService(String channel, BayeuxServerImpl bayeux, CometdConsumer consumer) {
            super(bayeux, channel);
            this.endpoint = consumer.getEndpoint();
            this.binding = createBinding(bayeux);
            this.consumer = consumer;
            this.channelName = channel;
            addService(channel, "push");
        }

        private CometdBinding createBinding(BayeuxServerImpl bayeux) {
            boolean enableSessionHeaders = endpoint == null ? false : endpoint.isSessionHeadersEnabled();
            return new CometdBinding(bayeux, enableSessionHeaders);
        }

        public void push(ServerSession remote, ServerMessage cometdMessage) throws Exception {
            Object data = null;

            Message message = binding.createCamelMessage(endpoint.getCamelContext(), remote, cometdMessage, data);

            Exchange exchange = endpoint.createExchange();
            exchange.setIn(message);

            consumer.getProcessor().process(exchange);

            if (ExchangeHelper.isOutCapable(exchange)) {
                ServerChannel channel = getBayeux().getChannel(channelName);
                ServerSession serverSession = getServerSession();

                ServerMessage.Mutable outMessage = binding.createCometdMessage(channel, serverSession, exchange.getOut());
                remote.deliver(serverSession, outMessage);
            }
        }

    }

}