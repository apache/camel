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

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.util.ObjectHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The PubNub producer.
 */
public class PubNubProducer extends DefaultAsyncProducer {
    private static final Logger LOG = LoggerFactory.getLogger(PubNubProducer.class);
    private final PubNubEndpoint endpoint;

    public PubNubProducer(PubNubEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        Callback pubnubCallback = pubnubCallback(exchange, callback);

        Operation operation = getOperation(exchange);
        LOG.trace("Executing {} operation", operation);
        switch (operation) {
        case PUBLISH: {
            String channel = exchange.getIn().getHeader(PubNubConstants.CHANNEL, String.class);
            channel = channel != null ? channel : endpoint.getChannel();
            Object body = exchange.getIn().getBody();
            if (ObjectHelper.isEmpty(body)) {
                exchange.setException(new CamelException("Can not publish empty message"));
                callback.done(true);
                return true;
            }
            LOG.trace("Sending message [{}] to channel [{}]", body, channel);
            if (body.getClass().isAssignableFrom(JSONObject.class)) {
                endpoint.getPubnub().publish(channel, (JSONObject)body, pubnubCallback);
            } else if (body.getClass().isAssignableFrom(JSONArray.class)) {
                endpoint.getPubnub().publish(channel, (JSONArray)body, pubnubCallback);
            } else {
                try {
                    endpoint.getPubnub().publish(channel, exchange.getIn().getMandatoryBody(String.class), pubnubCallback);
                } catch (InvalidPayloadException e) {
                    exchange.setException(e);
                    callback.done(true);
                    return true;
                }
            }
            break;
        }
        case GET_HISTORY: {
            endpoint.getPubnub().history(endpoint.getChannel(), false, pubnubCallback);
            break;
        }
        case GET_STATE: {
            String uuid = exchange.getIn().getHeader(PubNubConstants.UUID, String.class);
            endpoint.getPubnub().getState(endpoint.getChannel(), uuid != null ? uuid : endpoint.getUuid(), pubnubCallback);
            break;
        }
        case HERE_NOW: {
            endpoint.getPubnub().hereNow(endpoint.getChannel(), true, true, pubnubCallback);
            break;
        }
        case SET_STATE: {
            try {
                JSONObject state = exchange.getIn().getMandatoryBody(JSONObject.class);
                String uuid = exchange.getIn().getHeader(PubNubConstants.UUID, String.class);
                endpoint.getPubnub().setState(endpoint.getChannel(), uuid != null ? uuid : endpoint.getUuid(), state, pubnubCallback);
            } catch (InvalidPayloadException e) {
                exchange.setException(e);
                callback.done(true);
                return true;
            }
            break;
        }
        case WHERE_NOW: {
            String uuid = exchange.getIn().getHeader(PubNubConstants.UUID, String.class);
            endpoint.getPubnub().whereNow(uuid != null ? uuid : endpoint.getUuid(), pubnubCallback);
            break;
        }
        default:
            throw new UnsupportedOperationException(operation.toString());
        }
        return false;
    }

    private Callback pubnubCallback(final Exchange exchange, final AsyncCallback callback) {
        Callback pubnubCallback = new Callback() {
            @Override
            public void successCallback(String channel, Object message) {
                LOG.trace("PubNub response {}", message);
                exchange.getIn().setHeader(PubNubConstants.CHANNEL, channel);
                if (exchange.getPattern().isOutCapable()) {
                    exchange.getOut().copyFrom(exchange.getIn());
                    exchange.getOut().setBody(message);
                }
                callback.done(false);
            }

            @Override
            public void errorCallback(String channel, PubnubError error) {
                exchange.setException(new CamelException(error.toString()));
                callback.done(false);
            }
        };
        return pubnubCallback;
    }

    private Operation getOperation(Exchange exchange) {
        String operation = exchange.getIn().getHeader(PubNubConstants.OPERATION, String.class);
        if (operation == null) {
            operation = endpoint.getOperation();
        }
        return operation != null ? Operation.valueOf(operation) : Operation.PUBLISH;
    }

    private enum Operation {
        HERE_NOW, WHERE_NOW, GET_STATE, SET_STATE, GET_HISTORY, PUBLISH;
    }
}
