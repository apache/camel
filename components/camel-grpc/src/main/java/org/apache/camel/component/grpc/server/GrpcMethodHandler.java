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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.apache.camel.Exchange;
import org.apache.camel.component.grpc.GrpcConstants;
import org.apache.camel.component.grpc.GrpcConsumer;
import org.apache.camel.component.grpc.GrpcConsumerStrategy;
import org.apache.camel.component.grpc.GrpcEndpoint;

/**
 * Handles gRPC service method invocations
 */
public class GrpcMethodHandler {

    protected final GrpcConsumer consumer;

    public GrpcMethodHandler(GrpcConsumer consumer) {
        this.consumer = consumer;
    }

    public void handle(Object body, StreamObserver<Object> responseObserver, String methodName) throws Exception {
        Map<String, Object> grcpHeaders = populateGrpcHeaders(methodName);
        GrpcEndpoint endpoint = (GrpcEndpoint) consumer.getEndpoint();
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody(body);
        exchange.getIn().setHeaders(grcpHeaders);

        if (endpoint.isSynchronous()) {
            consumer.getProcessor().process(exchange);
        } else {
            consumer.getAsyncProcessor().process(exchange);
        }

        if (exchange.isFailed()) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription(exchange.getException().getMessage())
                    // This can be attached to the Status locally, but NOT transmitted to the client!
                    .withCause(exchange.getException())
                    .asRuntimeException());
        } else {
            Object responseBody = exchange.getIn().getBody();
            if (responseBody instanceof List) {
                List<Object> responseList = (List<Object>) responseBody;
                responseList.forEach(responseItem -> {
                    responseObserver.onNext(responseItem);
                });
            } else {
                responseObserver.onNext(responseBody);
            }
            responseObserver.onCompleted();
        }
    }

    public StreamObserver<Object> handleForConsumerStrategy(StreamObserver<Object> responseObserver, String methodName) {
        Map<String, Object> grcpHeaders = populateGrpcHeaders(methodName);
        GrpcEndpoint endpoint = (GrpcEndpoint) consumer.getEndpoint();
        StreamObserver<Object> requestObserver;

        if (consumer.getConfiguration().getConsumerStrategy() == GrpcConsumerStrategy.AGGREGATION) {
            requestObserver = new GrpcRequestAggregationStreamObserver(endpoint, consumer, responseObserver, grcpHeaders);
        } else if (consumer.getConfiguration().getConsumerStrategy() == GrpcConsumerStrategy.PROPAGATION) {
            requestObserver = new GrpcRequestPropagationStreamObserver(endpoint, consumer, responseObserver, grcpHeaders);
        } else {
            throw new IllegalArgumentException("gRPC processing strategy not implemented " + consumer.getConfiguration().getConsumerStrategy());
        }

        return requestObserver;
    }

    private Map<String, Object> populateGrpcHeaders(String methodName) {
        Map<String, Object> grpcHeaders = new HashMap<>();
        grpcHeaders.put(GrpcHeaderInterceptor.USER_AGENT_CONTEXT_KEY.toString(), GrpcHeaderInterceptor.USER_AGENT_CONTEXT_KEY.get());
        grpcHeaders.put(GrpcHeaderInterceptor.CONTENT_TYPE_CONTEXT_KEY.toString(), GrpcHeaderInterceptor.CONTENT_TYPE_CONTEXT_KEY.get());
        grpcHeaders.put(GrpcConstants.GRPC_METHOD_NAME_HEADER, methodName);
        return grpcHeaders;
    }
}
