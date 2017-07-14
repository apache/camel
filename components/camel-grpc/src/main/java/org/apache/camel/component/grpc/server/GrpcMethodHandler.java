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
package org.apache.camel.component.grpc.server;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.grpc.stub.StreamObserver;

import javassist.util.proxy.MethodHandler;

import org.apache.camel.Exchange;
import org.apache.camel.component.grpc.GrpcConstants;
import org.apache.camel.component.grpc.GrpcConsumer;
import org.apache.camel.component.grpc.GrpcConsumerStrategy;
import org.apache.camel.component.grpc.GrpcEndpoint;

/**
 * gRPC server method invocation handler
 */
public class GrpcMethodHandler implements MethodHandler {
    private final GrpcEndpoint endpoint;
    private final GrpcConsumer consumer;

    public GrpcMethodHandler(GrpcEndpoint endpoint, GrpcConsumer consumer) {
        this.endpoint = endpoint;
        this.consumer = consumer;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(Object self, Method thisMethod, Method proceed, Object[] args) throws Throwable {
        Map<String, Object> grcpHeaders = new HashMap<String, Object>();
        
        grcpHeaders.put(GrpcHeaderInterceptor.USER_AGENT_CONTEXT_KEY.toString(), GrpcHeaderInterceptor.USER_AGENT_CONTEXT_KEY.get());
        grcpHeaders.put(GrpcHeaderInterceptor.CONTENT_TYPE_CONTEXT_KEY.toString(), GrpcHeaderInterceptor.CONTENT_TYPE_CONTEXT_KEY.get());
        grcpHeaders.put(GrpcConstants.GRPC_METHOD_NAME_HEADER, thisMethod.getName());
        
        // Determines that the incoming parameters are transmitted in synchronous mode
        // Two incoming parameters and second is instance of the io.grpc.stub.StreamObserver
        if (args.length == 2 && args[1] instanceof StreamObserver) {
            Exchange exchange = endpoint.createExchange();
            exchange.getIn().setBody(args[0]);
            exchange.getIn().setHeaders(grcpHeaders);

            if (endpoint.isSynchronous()) {
                consumer.getProcessor().process(exchange);
            } else {
                consumer.getAsyncProcessor().process(exchange);
            }
            
            StreamObserver<Object> responseObserver = (StreamObserver<Object>)args[1];
            Object responseBody = exchange.getIn().getBody();
            if (responseBody instanceof List) {
                List<Object> responseList = (List<Object>)responseBody;
                responseList.forEach((responseItem) -> {
                    responseObserver.onNext(responseItem);
                });
            } else {
                responseObserver.onNext(responseBody);
            }
            responseObserver.onCompleted();
        } else if (args.length == 1 && args[0] instanceof StreamObserver) {
            // Single incoming parameter is instance of the io.grpc.stub.StreamObserver
            final StreamObserver<Object> responseObserver = (StreamObserver<Object>)args[0];
            StreamObserver<Object> requestObserver = null;
            
            if (consumer.getConfiguration().getConsumerStrategy() == GrpcConsumerStrategy.AGGREGATION) {
                requestObserver = new GrpcRequestAggregationStreamObserver(endpoint, consumer, responseObserver, grcpHeaders);
            } else if (consumer.getConfiguration().getConsumerStrategy() == GrpcConsumerStrategy.PROPAGATION) {
                requestObserver = new GrpcRequestPropagationStreamObserver(endpoint, consumer, responseObserver, grcpHeaders);
            } else {
                throw new IllegalArgumentException("gRPC processing strategy not implemented " + consumer.getConfiguration().getConsumerStrategy());
            }
            
            return requestObserver;
        } else {
            throw new IllegalArgumentException("Invalid to process gRPC method: " + thisMethod.getName());
        }

        return null;
    }
}
