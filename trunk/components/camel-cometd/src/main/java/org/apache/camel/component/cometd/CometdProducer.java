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
import org.apache.camel.impl.DefaultProducer;
import org.cometd.bayeux.server.BayeuxServer;
import org.cometd.bayeux.server.ServerChannel;
import org.cometd.bayeux.server.ServerSession;
import org.cometd.server.AbstractService;
import org.cometd.server.BayeuxServerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Producer to send messages using Cometd and Bayeux protocol.
 */
public class CometdProducer extends DefaultProducer implements CometdProducerConsumer {
    private static final transient Logger LOG = LoggerFactory.getLogger(CometdProducer.class);

    private BayeuxServerImpl bayeux;
    private ProducerService service;
    private final CometdEndpoint endpoint;

    public CometdProducer(CometdEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void start() throws Exception {
        super.start();
        // must connect first
        endpoint.connect(this);
        service = new ProducerService(getBayeux(), endpoint.getPath(), this);
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        endpoint.disconnect(this);
    }

    public void process(final Exchange exchange) {
        service.process(exchange);
    }

    public CometdEndpoint getEndpoint() {
        return endpoint;
    }

    public BayeuxServerImpl getBayeux() {
        return bayeux;
    }

    public void setBayeux(BayeuxServerImpl bayeux) {
        this.bayeux = bayeux;
    }

    public static class ProducerService extends AbstractService {

        private final CometdProducer producer;

        public ProducerService(BayeuxServer bayeux, String channel, CometdProducer producer) {
            super(bayeux, channel);
            this.producer = producer;
        }

        public void process(final Exchange exchange) {
            String channelName = producer.getEndpoint().getPath();
            BayeuxServerImpl bayeux = producer.getBayeux();
            ServerChannel channel = bayeux.getChannel(channelName);
            ServerSession serverSession = getServerSession();

            if (channel != null) {
                logDelivery(exchange, channel);
                channel.publish(serverSession, exchange.getIn().getBody(), null);
            }
        }

        private void logDelivery(Exchange exchange, ServerChannel channel) {
            if (LOG.isTraceEnabled()) {
                LOG.trace(String.format("Delivering to clients %s path: %s exchange: %s",
                        channel.getSubscribers(), channel, exchange));
            }
        }
    }
}
