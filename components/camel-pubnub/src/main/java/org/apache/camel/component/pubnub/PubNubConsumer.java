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
package org.apache.camel.component.pubnub;

import java.util.Arrays;

import com.pubnub.api.PubNub;
import com.pubnub.api.callbacks.SubscribeCallback;
import com.pubnub.api.models.consumer.PNStatus;
import com.pubnub.api.models.consumer.objects_api.channel.PNChannelMetadataResult;
import com.pubnub.api.models.consumer.objects_api.membership.PNMembershipResult;
import com.pubnub.api.models.consumer.objects_api.uuid.PNUUIDMetadataResult;
import com.pubnub.api.models.consumer.pubsub.PNMessageResult;
import com.pubnub.api.models.consumer.pubsub.PNPresenceEventResult;
import com.pubnub.api.models.consumer.pubsub.PNSignalResult;
import com.pubnub.api.models.consumer.pubsub.files.PNFileEventResult;
import com.pubnub.api.models.consumer.pubsub.message_actions.PNMessageActionResult;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.pubnub.api.enums.PNStatusCategory.PNTimeoutCategory;
import static com.pubnub.api.enums.PNStatusCategory.PNUnexpectedDisconnectCategory;
import static org.apache.camel.component.pubnub.PubNubConstants.CHANNEL;
import static org.apache.camel.component.pubnub.PubNubConstants.TIMETOKEN;

public class PubNubConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(PubNubConsumer.class);

    private final PubNubEndpoint endpoint;
    private final PubNubConfiguration pubNubConfiguration;

    public PubNubConsumer(PubNubEndpoint endpoint, Processor processor, PubNubConfiguration pubNubConfiguration) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.pubNubConfiguration = pubNubConfiguration;
    }

    private void initCommunication() {
        endpoint.getPubnub().addListener(new PubNubCallback());
        if (pubNubConfiguration.isWithPresence()) {
            endpoint.getPubnub().subscribe().channels(Arrays.asList(pubNubConfiguration.getChannel())).withPresence().execute();
        } else {
            endpoint.getPubnub().subscribe().channels(Arrays.asList(pubNubConfiguration.getChannel())).execute();
        }
    }

    private void terminateCommunication() {
        try {
            endpoint.getPubnub().unsubscribe().channels(Arrays.asList(pubNubConfiguration.getChannel())).execute();
        } catch (Exception e) {
            // ignore
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

    class PubNubCallback extends SubscribeCallback {

        @Override
        public void status(PubNub pubnub, PNStatus status) {
            if (status.getCategory() == PNUnexpectedDisconnectCategory || status.getCategory() == PNTimeoutCategory) {
                LOG.trace("Got status: {}. Reconnecting to PubNub", status);
                pubnub.reconnect();
            } else {
                LOG.trace("Status message: {}", status);
            }
        }

        @Override
        public void message(PubNub pubnub, PNMessageResult message) {
            Exchange exchange = createExchange(true);
            Message inmessage = exchange.getIn();
            inmessage.setBody(message);
            inmessage.setHeader(TIMETOKEN, message.getTimetoken());
            inmessage.setHeader(CHANNEL, message.getChannel());
            inmessage.setHeader(Exchange.MESSAGE_TIMESTAMP, message.getTimetoken());
            try {
                getProcessor().process(exchange);
            } catch (Exception e) {
                getExceptionHandler().handleException("Error processing exchange", e);
            }
        }

        @Override
        public void presence(PubNub pubnub, PNPresenceEventResult presence) {
            Exchange exchange = createExchange(true);
            Message inmessage = exchange.getIn();
            inmessage.setBody(presence);
            inmessage.setHeader(TIMETOKEN, presence.getTimetoken());
            inmessage.setHeader(CHANNEL, presence.getChannel());
            inmessage.setHeader(Exchange.MESSAGE_TIMESTAMP, presence.getTimetoken());
            try {
                getProcessor().process(exchange);
            } catch (Exception e) {
                getExceptionHandler().handleException("Error processing exchange", e);
            }
        }

        /**
         * signal, user, space, membership, messageAction, presence, and file listeners are mandatory and you MUST at
         * least provide no op implementations for these listeners
         *
         * @param pubnub
         * @param pnSignalResult
         */
        @Override
        public void signal(@NotNull PubNub pubnub, @NotNull PNSignalResult pnSignalResult) {
            LOG.trace("signal: {}.", pnSignalResult);
        }

        @Override
        public void uuid(@NotNull PubNub pubnub, @NotNull PNUUIDMetadataResult pnUUIDMetadataResult) {
            LOG.trace("uuid: {}.", pnUUIDMetadataResult);
        }

        @Override
        public void channel(@NotNull PubNub pubnub, @NotNull PNChannelMetadataResult pnChannelMetadataResult) {
            LOG.trace("uuid: {}.", pnChannelMetadataResult);
        }

        @Override
        public void membership(@NotNull PubNub pubnub, @NotNull PNMembershipResult pnMembershipResult) {
            LOG.trace("membership: {}.", pnMembershipResult);
        }

        @Override
        public void messageAction(@NotNull PubNub pubnub, @NotNull PNMessageActionResult pnMessageActionResult) {
            LOG.trace("messageAction: {}.", pnMessageActionResult);
        }

        @Override
        public void file(@NotNull PubNub pubnub, @NotNull PNFileEventResult pnFileEventResult) {
            LOG.trace("file: {}.", pnFileEventResult);
        }
    }

}
