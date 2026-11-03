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
package org.apache.camel.component.grpc;

import java.util.List;

import io.grpc.stub.StreamObserver;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.grpc.server.GrpcMethodHandler;
import org.apache.camel.support.DefaultProducer;

public class GrpcProducerToRouteControlledStreamObserver extends DefaultProducer {
    public GrpcProducerToRouteControlledStreamObserver(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        StreamObserver<Object> observer = exchange.getProperty(
                GrpcConstants.GRPC_RESPONSE_OBSERVER, StreamObserver.class);
        if (observer == null) {
            throw new IllegalArgumentException(
                    "The exchange has no " + GrpcConstants.GRPC_RESPONSE_OBSERVER + " property: it did not come from a"
                                               + " gRPC consumer with routeControlledStreamObserver=true (with"
                                               + " inheritExchangePropertiesForReplies=true for the replies of a gRPC producer)");
        }
        String eventType = exchange.getMessage().getHeader(
                GrpcConstants.GRPC_EVENT_TYPE_HEADER, String.class);
        if (eventType == null) {
            // nothing is sent: the event to send to the client is unknown
            throw new IllegalArgumentException(
                    "The " + GrpcConstants.GRPC_EVENT_TYPE_HEADER + " header must be set to "
                                               + GrpcConstants.GRPC_EVENT_TYPE_ON_NEXT + ", "
                                               + GrpcConstants.GRPC_EVENT_TYPE_ON_COMPLETED + " or "
                                               + GrpcConstants.GRPC_EVENT_TYPE_ON_ERROR
                                               + " to send a reply with toRouteControlledStreamObserver");
        }
        switch (eventType) {
            case GrpcConstants.GRPC_EVENT_TYPE_ON_NEXT -> onNext(exchange, observer);
            case GrpcConstants.GRPC_EVENT_TYPE_ON_COMPLETED -> observer.onCompleted();
            case GrpcConstants.GRPC_EVENT_TYPE_ON_ERROR -> observer.onError((Throwable) exchange.getMessage().getBody());
            default -> {
                // NO-OP
            }
        }
    }

    /**
     * Sends the body to the client: each element of a List body, or the body itself, converted to the response type of
     * the method that the consumer set as exchange property. When a message cannot be converted, nothing is sent, the
     * call ends with an error and the exchange fails, as the client would otherwise decode the bytes of another message
     * type (for example the unchanged request) as the response.
     */
    private void onNext(Exchange exchange, StreamObserver<Object> observer) {
        Class<?> responseType = exchange.getProperty(GrpcConstants.GRPC_RESPONSE_TYPE, Class.class);
        List<Object> responses;
        try {
            responses = GrpcMethodHandler.toResponses(exchange, exchange.getMessage().getBody(), responseType);
        } catch (IllegalArgumentException e) {
            observer.onError(GrpcMethodHandler.toStatusException((GrpcEndpoint) getEndpoint(), e));
            throw e;
        }
        responses.forEach(observer::onNext);
    }
}
