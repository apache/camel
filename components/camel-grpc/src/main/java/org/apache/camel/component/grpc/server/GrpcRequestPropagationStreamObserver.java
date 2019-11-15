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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import io.grpc.stub.StreamObserver;
import org.apache.camel.component.grpc.GrpcConsumer;
import org.apache.camel.component.grpc.GrpcEndpoint;

/**
 * gRPC request stream observer which is propagating every onNext(), onError()
 * or onCompleted() calls to the Camel route
 */
public class GrpcRequestPropagationStreamObserver extends GrpcRequestAbstractStreamObserver {

    public GrpcRequestPropagationStreamObserver(GrpcEndpoint endpoint, GrpcConsumer consumer, StreamObserver<Object> responseObserver, Map<String, Object> headers) {
        super(endpoint, consumer, responseObserver, headers);
    }

    @Override
    public void onNext(Object request) {
        CountDownLatch latch = new CountDownLatch(1);
        Object responseBody = null;
        
        exchange = endpoint.createExchange();
        exchange.getIn().setBody(request);
        exchange.getIn().setHeaders(headers);
        
        consumer.process(exchange, doneSync -> {
            latch.countDown();
        });
        
        try {
            latch.await();
            
            if (exchange.hasOut()) {
                responseBody = exchange.getOut().getBody();
            } else {
                responseBody = exchange.getIn().getBody();
            }
            
            if (responseBody instanceof List) {
                List<?> responseList = (List<?>)responseBody;
                responseList.forEach(responseObserver::onNext);
            } else {
                responseObserver.onNext(responseBody);
            }
            responseObserver.onCompleted();

        } catch (InterruptedException e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        exchange = endpoint.createExchange();
        exchange.getIn().setHeaders(headers);
        consumer.onError(exchange, throwable);
        responseObserver.onError(throwable);
    }

    @Override
    public void onCompleted() {
        exchange = endpoint.createExchange();
        exchange.getIn().setHeaders(headers);
        consumer.onCompleted(exchange);
        responseObserver.onCompleted();
    }
}
