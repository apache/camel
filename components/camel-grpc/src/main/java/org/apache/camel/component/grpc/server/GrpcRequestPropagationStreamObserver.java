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
package org.apache.camel.component.grpc.server;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import io.grpc.stub.StreamObserver;
import org.apache.camel.Exchange;
import org.apache.camel.component.grpc.GrpcConsumer;
import org.apache.camel.component.grpc.GrpcEndpoint;

/**
 * This is the default consumer strategy for client-streaming and bi-directional streaming gRPC calls. gRPC request
 * stream observer which is propagating every onNext(), onError() or onCompleted() calls to the Camel route, one event
 * at a time.
 * <p>
 * With routeControlledStreamObserver=true the route sends the replies and ends the call itself, as with the DELEGATION
 * strategy: the consumer sends nothing to the client.
 */
public class GrpcRequestPropagationStreamObserver extends GrpcRequestAbstractStreamObserver {

    private final boolean routeControlled;
    // set once the call ended with an error, by a failed exchange or by the route: nothing more can be sent to the
    // client. Shared by the exchanges of all the events of the call when the route controls the replies
    private final AtomicBoolean failed = new AtomicBoolean();

    public GrpcRequestPropagationStreamObserver(GrpcEndpoint endpoint, GrpcConsumer consumer,
                                                StreamObserver<Object> responseObserver, Map<String, Object> headers) {
        super(endpoint, consumer, responseObserver, headers);
        this.routeControlled = endpoint.getConfiguration().isRouteControlledStreamObserver();
    }

    @Override
    public void onNext(Object request) {
        if (failed.get()) {
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);

        exchange = endpoint.createExchange();
        exchange.getIn().setBody(request);
        exchange.getIn().setHeaders(headers);
        setRouteControlledProperties(exchange);

        consumer.process(exchange, doneSync -> latch.countDown());

        try {
            latch.await();

            if (routeControlled) {
                // the route sends the replies itself
                return;
            }
            if (sendFailure(exchange) || !sendResponses(exchange)) {
                failed.set(true);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (failed.compareAndSet(false, true)) {
                responseObserver.onError(e);
            }
        }
    }

    @Override
    public void onError(Throwable throwable) {
        exchange = endpoint.createExchange();
        exchange.getIn().setHeaders(headers);
        setRouteControlledProperties(exchange);
        consumer.onError(exchange, throwable);
        if (!routeControlled && !failed.get()) {
            responseObserver.onError(throwable);
        }
    }

    @Override
    public void onCompleted() {
        exchange = endpoint.createExchange();
        exchange.getIn().setHeaders(headers);
        setRouteControlledProperties(exchange);
        consumer.onCompleted(exchange);
        if (!routeControlled && !failed.get()) {
            responseObserver.onCompleted();
        }
    }

    private void setRouteControlledProperties(Exchange exchange) {
        if (routeControlled) {
            GrpcMethodHandler.setRouteControlledProperties(endpoint, exchange, responseObserver, responseType, failed);
        }
    }
}
