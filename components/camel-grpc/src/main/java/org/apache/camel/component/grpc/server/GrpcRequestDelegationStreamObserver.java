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

import io.grpc.stub.StreamObserver;
import org.apache.camel.component.grpc.GrpcConstants;
import org.apache.camel.component.grpc.GrpcConsumer;
import org.apache.camel.component.grpc.GrpcEndpoint;

public class GrpcRequestDelegationStreamObserver extends GrpcRequestAbstractStreamObserver {

    public GrpcRequestDelegationStreamObserver(GrpcEndpoint endpoint, GrpcConsumer consumer,
                                               StreamObserver<Object> responseObserver, Map<String, Object> headers) {
        super(endpoint, consumer, responseObserver, headers);
        if (!endpoint.getConfiguration().isRouteControlledStreamObserver()) {
            throw new IllegalStateException(
                    "DELEGATION consumer strategy must be used with enabled routeControlledStreamObserver");
        }
    }

    @Override
    public void onNext(Object request) {
        var exchange = endpoint.createExchange();
        exchange.getIn().setBody(request);
        exchange.getIn().setHeaders(headers);
        exchange.setProperty(GrpcConstants.GRPC_RESPONSE_OBSERVER, responseObserver);
        consumer.process(exchange, doneSync -> {
        });
    }

    @Override
    public void onError(Throwable throwable) {
        var exchange = endpoint.createExchange();
        exchange.getIn().setHeaders(headers);
        exchange.setProperty(GrpcConstants.GRPC_RESPONSE_OBSERVER, responseObserver);
        consumer.onError(exchange, throwable);
    }

    @Override
    public void onCompleted() {
        var exchange = endpoint.createExchange();
        exchange.getIn().setHeaders(headers);
        exchange.setProperty(GrpcConstants.GRPC_RESPONSE_OBSERVER, responseObserver);
        consumer.onCompleted(exchange);
    }
}
