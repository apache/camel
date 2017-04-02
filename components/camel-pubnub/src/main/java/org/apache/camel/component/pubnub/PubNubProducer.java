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

import java.util.Arrays;

import com.pubnub.api.callbacks.PNCallback;
import com.pubnub.api.models.consumer.PNPublishResult;
import com.pubnub.api.models.consumer.PNStatus;
import com.pubnub.api.models.consumer.history.PNHistoryResult;
import com.pubnub.api.models.consumer.presence.PNGetStateResult;
import com.pubnub.api.models.consumer.presence.PNHereNowResult;
import com.pubnub.api.models.consumer.presence.PNSetStateResult;
import com.pubnub.api.models.consumer.presence.PNWhereNowResult;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The PubNub producer.
 */
public class PubNubProducer extends DefaultAsyncProducer {
    private static final Logger LOG = LoggerFactory.getLogger(PubNubProducer.class);
    private final PubNubEndpoint endpoint;
    private final PubNubConfiguration pubnubConfiguration;

    public PubNubProducer(PubNubEndpoint endpoint, PubNubConfiguration pubNubConfiguration) {
        super(endpoint);
        this.endpoint = endpoint;
        this.pubnubConfiguration = pubNubConfiguration;
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {

        Operation operation = getOperation(exchange);

        LOG.debug("Executing {} operation", operation);

        switch (operation) {
        case PUBLISH: {
            doPublish(exchange, callback);
            break;
        }
        case FIRE: {
            doFire(exchange, callback);
            break;
        }
        case GET_HISTORY: {
            doGetHistory(exchange, callback);
            break;
        }
        case GET_STATE: {
            doGetState(exchange, callback);
            break;
        }
        case HERE_NOW: {
            doHereNow(exchange, callback);
            break;
        }
        case SET_STATE: {
            doSetState(exchange, callback);
            break;
        }
        case WHERE_NOW: {
            doWhereNow(exchange, callback);
            break;
        }
        default:
            throw new UnsupportedOperationException(operation.toString());
        }
        return false;
    }


    private void doPublish(Exchange exchange, AsyncCallback callback) {
        Object body = exchange.getIn().getBody();
        if (ObjectHelper.isEmpty(body)) {
            exchange.setException(new CamelException("Can not publish empty message"));
            callback.done(true);
        }
        LOG.debug("Sending message [{}] to channel [{}]", body, getChannel(exchange));
        endpoint.getPubnub()
            .publish()
            .message(body)
            .channel(getChannel(exchange))
            .usePOST(true)
            .async(new PNCallback<PNPublishResult>() {
                @Override
                public void onResponse(PNPublishResult result, PNStatus status) {
                    if (!status.isError()) {
                        exchange.getIn().setHeader(PubNubConstants.TIMETOKEN, result.getTimetoken());
                    }
                    processMessage(exchange, callback, status, null);
                }
            });
    }

    private void doFire(Exchange exchange, AsyncCallback callback) {
        Object body = exchange.getIn().getBody();
        if (ObjectHelper.isEmpty(body)) {
            exchange.setException(new CamelException("Can not fire empty message"));
            callback.done(true);
        }
        LOG.debug("Sending message [{}] to channel [{}]", body, getChannel(exchange));
        endpoint.getPubnub()
            .fire()
            .message(body)
            .channel(getChannel(exchange))
            .async(new PNCallback<PNPublishResult>() {
                @Override
                public void onResponse(PNPublishResult result, PNStatus status) {
                    if (!status.isError()) {
                        exchange.getIn().setHeader(PubNubConstants.TIMETOKEN, result.getTimetoken());
                    }
                    processMessage(exchange, callback, status, null);
                }
            });
    }

    private void doGetHistory(Exchange exchange, AsyncCallback callback) {
        // @formatter:off
        endpoint.getPubnub()
            .history()
            .channel(getChannel(exchange))
            .async(new PNCallback<PNHistoryResult>() {
                @Override
                public void onResponse(PNHistoryResult result, PNStatus status) {
                    LOG.debug("Got history message [{}]", result);
                    processMessage(exchange, callback, status, result.getMessages());
                }
            });
    // @formatter:on
    }

    private void doSetState(Exchange exchange, AsyncCallback callback) {
        Object body = exchange.getIn().getBody();
        if (ObjectHelper.isEmpty(body)) {
            exchange.setException(new CamelException("Can not publish empty message"));
            callback.done(true);
        }
        LOG.debug("Sending setState [{}] to channel [{}]", body, getChannel(exchange));
        endpoint.getPubnub()
            .setPresenceState()
            .channels(Arrays.asList(getChannel(exchange)))
            .state(body)
            .uuid(getUUID(exchange))
            .async(new PNCallback<PNSetStateResult>() {
                public void onResponse(PNSetStateResult result, PNStatus status) {
                    LOG.debug("Got setState responsee [{}]", result);
                    processMessage(exchange, callback, status, result);
                };
            });
    }

    private void doGetState(Exchange exchange, AsyncCallback callback) {
        // @formatter:off
        endpoint.getPubnub()
            .getPresenceState()
            .channels(Arrays.asList(getChannel(exchange)))
            .uuid(getUUID(exchange))
            .async(new PNCallback<PNGetStateResult>() {
                @Override
                public void onResponse(PNGetStateResult result, PNStatus status) {
                    LOG.debug("Got state [{}]", result.getStateByUUID());
                    processMessage(exchange, callback, status, result.getStateByUUID());
                }
            });
    // @formatter:on
    }

    private void doHereNow(Exchange exchange, AsyncCallback callback) {
        endpoint.getPubnub()
            .hereNow()
            .channels(Arrays.asList(getChannel(exchange)))
            .includeState(true)
            .includeUUIDs(true)
            .async(new PNCallback<PNHereNowResult>() {
                @Override
                public void onResponse(PNHereNowResult result, PNStatus status) {
                    LOG.debug("Got herNow message [{}]", result);
                    processMessage(exchange, callback, status, result);
                }
            });
    }

    private void doWhereNow(Exchange exchange, AsyncCallback callback) {
        // @formatter:off
        endpoint.getPubnub()
            .whereNow()
            .uuid(getUUID(exchange))
            .async(new PNCallback<PNWhereNowResult>() {
                @Override
                public void onResponse(PNWhereNowResult result, PNStatus status) {
                    LOG.debug("Got whereNow message [{}]", result.getChannels());
                    processMessage(exchange, callback, status, result.getChannels());
                };
            });
        // @formatter:on
    }

    private void processMessage(Exchange exchange, AsyncCallback callback, PNStatus status, Object body) {
        if (status.isError()) {
            exchange.setException(status.getErrorData().getThrowable());
            callback.done(true);
        } else if (body != null) {
            exchange.getIn().setBody(body);
        }
        if (exchange.getPattern().isOutCapable()) {
            exchange.getOut().copyFrom(exchange.getIn());
            if (body != null) {
                exchange.getOut().setBody(body);
            }
        }

        // signal exchange completion
        callback.done(false);
    }

    private Operation getOperation(Exchange exchange) {
        String operation = exchange.getIn().getHeader(PubNubConstants.OPERATION, String.class);
        if (operation == null) {
            operation = pubnubConfiguration.getOperation();
        }
        return operation != null ? Operation.valueOf(operation) : Operation.PUBLISH;
    }

    private String getChannel(Exchange exchange) {
        String channel = exchange.getIn().getHeader(PubNubConstants.CHANNEL, String.class);
        return channel != null ? channel : pubnubConfiguration.getChannel();
    }

    private String getUUID(Exchange exchange) {
        String uuid = exchange.getIn().getHeader(PubNubConstants.UUID, String.class);
        return uuid != null ? uuid : pubnubConfiguration.getUuid();
    }

    private enum Operation {
        HERE_NOW, WHERE_NOW, GET_STATE, SET_STATE, GET_HISTORY, PUBLISH, FIRE;
    }
}
