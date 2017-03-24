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
package org.apache.camel.component.pubnub;

import com.pubnub.api.Callback;
import com.pubnub.api.PubnubError;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.impl.DefaultExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The PubNub consumer.
 */
public class PubNubConsumer extends DefaultConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(PubNubConsumer.class);
    private final PubNubEndpoint endpoint;

    public PubNubConsumer(PubNubEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    private void initCommunication() throws Exception {
        if (endpoint.getEndpointType().equals(PubNubEndpointType.pubsub)) {
            endpoint.getPubnub().subscribe(endpoint.getChannel(), new PubNubCallback());
        } else {
            endpoint.getPubnub().presence(endpoint.getChannel(), new PubNubCallback());
        }
    }

    private void terminateCommunication() throws Exception {
        if (endpoint.getEndpointType().equals(PubNubEndpointType.pubsub)) {
            endpoint.getPubnub().unsubscribe(endpoint.getChannel());
        } else {
            endpoint.getPubnub().unsubscribePresence(endpoint.getChannel());
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        initCommunication();
    }

    @Override
    protected void doResume() throws Exception {
        super.doResume();
        initCommunication();
    }

    @Override
    protected void doStop() throws Exception {
        terminateCommunication();
        super.doStop();
    }

    @Override
    protected void doSuspend() throws Exception {
        terminateCommunication();
        super.doSuspend();
    }

    private class PubNubCallback extends Callback {

        @Override
        public void successCallback(String channel, Object objectMessage, String timetoken) {
            Exchange exchange = new DefaultExchange(endpoint, endpoint.getExchangePattern());
            Message message = exchange.getIn();
            message.setBody(objectMessage);
            message.setHeader(PubNubConstants.TIMETOKEN, timetoken);
            message.setHeader(PubNubConstants.CHANNEL, channel);
            try {
                getProcessor().process(exchange);
            } catch (Exception e) {
                exchange.setException(e);
                getExceptionHandler().handleException("Error processing exchange", exchange, e);
            }
        }

        @Override
        public void connectCallback(String channel, Object message) {
            LOG.info("Subscriber : Successfully connected to PubNub channel {}", channel);
        }

        @Override
        public void errorCallback(String channel, PubnubError error) {
            LOG.error("Subscriber : Error [{}] received from PubNub on channel {}", error, channel);
        }

        @Override
        public void reconnectCallback(String channel, Object message) {
            LOG.info("Subscriber : Reconnected to PubNub channel {}", channel);
        }

        @Override
        public void disconnectCallback(String channel, Object message) {
            LOG.trace("Subscriber : Disconnected from PubNub channel {}", channel);
        }
    }

}
