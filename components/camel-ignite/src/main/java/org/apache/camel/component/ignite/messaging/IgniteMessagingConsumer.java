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
package org.apache.camel.component.ignite.messaging;

import java.util.UUID;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.ignite.IgniteConstants;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.ignite.IgniteMessaging;
import org.apache.ignite.lang.IgniteBiPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ignite Messaging consumer.
 */
public class IgniteMessagingConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(IgniteMessagingConsumer.class);

    private IgniteMessagingEndpoint endpoint;
    private IgniteMessaging messaging;

    private IgniteBiPredicate<UUID, Object> predicate = new IgniteBiPredicate<UUID, Object>() {
        private static final long serialVersionUID = -971933058406324501L;

        @Override
        public boolean apply(UUID uuid, Object payload) {
            Exchange exchange = endpoint.createExchange(ExchangePattern.InOnly);
            Message in = exchange.getIn();
            in.setBody(payload);
            in.setHeader(IgniteConstants.IGNITE_MESSAGING_TOPIC, endpoint.getTopic());
            in.setHeader(IgniteConstants.IGNITE_MESSAGING_UUID, uuid);
            try {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Processing Ignite message for subscription {} with payload {}.", uuid, payload);
                }
                getProcessor().process(exchange);
            } catch (Exception e) {
                LOG.error(String.format("Exception while processing Ignite Message from topic %s", endpoint.getTopic()), e);
            }
            return true;
        }
    };

    public IgniteMessagingConsumer(IgniteMessagingEndpoint endpoint, Processor processor, IgniteMessaging messaging) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.messaging = messaging;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        messaging.localListen(endpoint.getTopic(), predicate);
        
        LOG.info("Started Ignite Messaging consumer for topic {}.", endpoint.getTopic());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        messaging.stopLocalListen(endpoint.getTopic(), predicate);
        
        LOG.info("Stopped Ignite Messaging consumer for topic {}.", endpoint.getTopic());
    }

}
