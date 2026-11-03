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
import java.util.concurrent.atomic.AtomicBoolean;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.grpc.server.GrpcMethodHandler;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrpcProducerToRouteControlledStreamObserver extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcProducerToRouteControlledStreamObserver.class);

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
            throw eventTypeRequired("must be set to");
        }
        // shared by the exchanges of the call; an exchange without it at least does not send twice on redelivery
        AtomicBoolean failed = exchange.getProperty(GrpcConstants.GRPC_RESPONSE_OBSERVER_FAILED, AtomicBoolean.class);
        if (failed == null) {
            failed = new AtomicBoolean();
            exchange.setProperty(GrpcConstants.GRPC_RESPONSE_OBSERVER_FAILED, failed);
        }
        switch (eventType) {
            case GrpcConstants.GRPC_EVENT_TYPE_ON_NEXT -> onNext(exchange, observer, failed);
            case GrpcConstants.GRPC_EVENT_TYPE_ON_COMPLETED -> {
                if (!isFailed(exchange, failed, eventType)) {
                    observer.onCompleted();
                }
            }
            case GrpcConstants.GRPC_EVENT_TYPE_ON_ERROR -> onError(exchange, observer, failed);
            default -> throw eventTypeRequired("is " + eventType + ", but must be");
        }
    }

    private static IllegalArgumentException eventTypeRequired(String problem) {
        return new IllegalArgumentException(
                "The " + GrpcConstants.GRPC_EVENT_TYPE_HEADER + " header " + problem + " "
                                            + GrpcConstants.GRPC_EVENT_TYPE_ON_NEXT + ", "
                                            + GrpcConstants.GRPC_EVENT_TYPE_ON_COMPLETED + " or "
                                            + GrpcConstants.GRPC_EVENT_TYPE_ON_ERROR
                                            + " to send a reply with toRouteControlledStreamObserver");
    }

    /**
     * Whether the call already ended with an error, so the event is ignored: the call is closed and nothing more can be
     * sent to the client, for example the forwarded onCompleted event after a reply that did not convert.
     */
    private static boolean isFailed(Exchange exchange, AtomicBoolean failed, String eventType) {
        if (failed.get()) {
            LOG.debug("Ignoring the {} event of exchange {}: the call already ended with an error", eventType,
                    exchange.getExchangeId());
            return true;
        }
        return false;
    }

    /**
     * Sends the body to the client: each element of a List body, or the body itself, converted to the response type of
     * the method that the consumer set as exchange property. When a message cannot be converted, nothing is sent, the
     * call ends with an error and the exchange fails, as the client would otherwise decode the bytes of another message
     * type (for example the unchanged request) as the response. A redelivery fails the same way, without a second error
     * on the closed call.
     */
    private static void onNext(Exchange exchange, StreamObserver<Object> observer, AtomicBoolean failed) {
        Class<?> responseType = exchange.getProperty(GrpcConstants.GRPC_RESPONSE_TYPE, Class.class);
        List<Object> responses;
        try {
            responses = GrpcMethodHandler.toResponses(exchange, exchange.getMessage().getBody(), responseType);
        } catch (IllegalArgumentException e) {
            if (failed.compareAndSet(false, true)) {
                // the muteException option of the consumer, muted when unknown
                boolean muteException = exchange.getProperty(GrpcConstants.GRPC_MUTE_EXCEPTION, true, Boolean.class);
                observer.onError(GrpcMethodHandler.toStatusException(muteException, e));
            }
            throw e;
        }
        if (!isFailed(exchange, failed, GrpcConstants.GRPC_EVENT_TYPE_ON_NEXT)) {
            responses.forEach(observer::onNext);
        }
    }

    /**
     * Ends the call with the Throwable in the body. Another body is a mistake of the route: the call still ends with an
     * INTERNAL status error with the text of the body as description, so the client does not wait until its deadline,
     * and the exchange fails.
     */
    private static void onError(Exchange exchange, StreamObserver<Object> observer, AtomicBoolean failed) {
        Object body = exchange.getMessage().getBody();
        if (body instanceof Throwable throwable) {
            if (!isFailed(exchange, failed, GrpcConstants.GRPC_EVENT_TYPE_ON_ERROR)) {
                failed.set(true);
                observer.onError(throwable);
            }
            return;
        }
        IllegalArgumentException e = new IllegalArgumentException(
                "The body of the " + GrpcConstants.GRPC_EVENT_TYPE_ON_ERROR
                                                                  + " event must be a Throwable, but was "
                                                                  + (body == null ? "null" : "a " + body.getClass().getName()));
        if (failed.compareAndSet(false, true)) {
            String description = exchange.getMessage().getBody(String.class);
            observer.onError(Status.INTERNAL.withDescription(description).withCause(e).asRuntimeException());
        }
        throw e;
    }
}
