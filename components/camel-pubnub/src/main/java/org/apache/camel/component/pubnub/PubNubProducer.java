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

import java.util.List;

import com.pubnub.api.PubNubException;
import com.pubnub.api.models.consumer.PNPublishResult;
import com.pubnub.api.models.consumer.history.PNHistoryResult;
import com.pubnub.api.models.consumer.presence.PNGetStateResult;
import com.pubnub.api.models.consumer.presence.PNHereNowResult;
import com.pubnub.api.models.consumer.presence.PNSetStateResult;
import com.pubnub.api.v2.callbacks.Result;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.ExchangeHelper;
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
        try {
            switch (operation) {
                case PUBLISH: {
                    doPublish(exchange, callback);
                    break;
                }
                case FIRE: {
                    doFire(exchange, callback);
                    break;
                }
                case GETHISTORY: {
                    doGetHistory(exchange, callback);
                    break;
                }
                case GETSTATE: {
                    doGetState(exchange, callback);
                    break;
                }
                case HERENOW: {
                    doHereNow(exchange, callback);
                    break;
                }
                case SETSTATE: {
                    doSetState(exchange, callback);
                    break;
                }
                default:
                    throw new UnsupportedOperationException(operation.toString());
            }
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }
        return false;
    }

    private void doPublish(Exchange exchange, AsyncCallback callback) {
        Object body = exchange.getIn().getBody();
        if (ObjectHelper.isEmpty(body)) {
            throw new RuntimeCamelException("Cannot publish empty message");
        }
        LOG.debug("Sending message [{}] to channel [{}]", body, getChannel(exchange));
        endpoint.getPubnub()
                .publish()
                .message(body)
                .channel(getChannel(exchange))
                .usePOST(true)
                .async((Result<PNPublishResult> result) -> {
                    LOG.debug("Got publish message [{}]", result);
                    if (result.isFailure()) {
                        PubNubException ex = result.exceptionOrNull();
                        if (ex != null) {
                            exchange.setException(ex);
                        }
                        callback.done(false);
                    } else {
                        PNPublishResult r = result.getOrNull();
                        if (r != null) {
                            exchange.getIn().setHeader(PubNubConstants.TIMETOKEN, r.getTimetoken());
                        }
                        processMessage(exchange, callback, null);
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
                .async((Result<PNPublishResult> result) -> {
                    LOG.debug("Got fire message [{}]", result);
                    if (result.isFailure()) {
                        PubNubException ex = result.exceptionOrNull();
                        if (ex != null) {
                            exchange.setException(ex);
                        }
                        callback.done(false);
                    } else {
                        PNPublishResult r = result.getOrNull();
                        if (r != null) {
                            exchange.getIn().setHeader(PubNubConstants.TIMETOKEN, r.getTimetoken());
                        }
                        processMessage(exchange, callback, null);
                    }
                });
    }

    private void doGetHistory(Exchange exchange, AsyncCallback callback) {
        endpoint.getPubnub().history().channel(getChannel(exchange)).async((Result<PNHistoryResult> result) -> {
            LOG.debug("Got history message [{}]", result);
            if (result.isFailure()) {
                PubNubException ex = result.exceptionOrNull();
                if (ex != null) {
                    exchange.setException(ex);
                }
                callback.done(false);
            } else {
                PNHistoryResult r = result.getOrNull();
                processMessage(exchange, callback, r != null ? r.getMessages() : null);
            }
        });
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
                .channels(List.of(getChannel(exchange)))
                .state(body)
                .uuid(getUUID(exchange))
                .async((Result<PNSetStateResult> result) -> {
                    LOG.debug("Got setState response [{}]", result);
                    if (result.isFailure()) {
                        PubNubException ex = result.exceptionOrNull();
                        if (ex != null) {
                            exchange.setException(ex);
                        }
                        callback.done(false);
                    } else {
                        PNSetStateResult r = result.getOrNull();
                        processMessage(exchange, callback, r);
                    }
                });
    }

    private void doGetState(Exchange exchange, AsyncCallback callback) {
        endpoint.getPubnub()
                .getPresenceState()
                .channels(List.of(getChannel(exchange)))
                .uuid(getUUID(exchange))
                .async((Result<PNGetStateResult> result) -> {
                    LOG.debug("Got state [{}]", result);
                    if (result.isFailure()) {
                        PubNubException ex = result.exceptionOrNull();
                        if (ex != null) {
                            exchange.setException(ex);
                        }
                        callback.done(false);
                    } else {
                        PNGetStateResult r = result.getOrNull();
                        processMessage(exchange, callback, r);
                    }
                });
    }

    private void doHereNow(Exchange exchange, AsyncCallback callback) {
        endpoint.getPubnub()
                .hereNow()
                .channels(List.of(getChannel(exchange)))
                .includeState(true)
                .includeUUIDs(true)
                .async((Result<PNHereNowResult> result) -> {
                    LOG.debug("Got herNow message [{}]", result);
                    if (result.isFailure()) {
                        PubNubException ex = result.exceptionOrNull();
                        if (ex != null) {
                            exchange.setException(ex);
                        }
                        callback.done(false);
                    } else {
                        PNHereNowResult r = result.getOrNull();
                        processMessage(exchange, callback, r);
                    }
                });
    }

    private void processMessage(Exchange exchange, AsyncCallback callback, Object body) {
        if (body != null) {
            ExchangeHelper.setInOutBodyPatternAware(exchange, body);
        }

        // signal exchange completion
        callback.done(false);
    }

    private Operation getOperation(Exchange exchange) {
        String operation = exchange.getIn().getHeader(PubNubConstants.OPERATION, String.class);
        if (operation == null) {
            operation = pubnubConfiguration.getOperation();
        }
        return operation != null ? Operation.valueOf(operation.toUpperCase()) : Operation.PUBLISH;
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
        HERENOW,
        GETSTATE,
        SETSTATE,
        GETHISTORY,
        PUBLISH,
        FIRE;
    }
}
