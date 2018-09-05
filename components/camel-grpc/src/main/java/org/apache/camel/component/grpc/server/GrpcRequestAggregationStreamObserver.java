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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import io.grpc.stub.StreamObserver;
import org.apache.camel.component.grpc.GrpcConsumer;
import org.apache.camel.component.grpc.GrpcEndpoint;

/**
 * gRPC request stream observer which is collecting received objects every
 * onNext() call into the list and processing them in onCompleted()
 */
public class GrpcRequestAggregationStreamObserver extends GrpcRequestAbstractStreamObserver {
    private List<Object> requestList = new LinkedList<>();

    public GrpcRequestAggregationStreamObserver(GrpcEndpoint endpoint, GrpcConsumer consumer, StreamObserver<Object> responseObserver, Map<String, Object> headers) {
        super(endpoint, consumer, responseObserver, headers);
        exchange = endpoint.createExchange();
    }

    @Override
    public void onNext(Object request) {
        requestList.add(request);
    }

    @Override
    public void onError(Throwable t) {
        exchange.setException(t);
    }

    @Override
    public void onCompleted() {
        CountDownLatch latch = new CountDownLatch(1);
        Object responseBody = null;
        
        exchange.getIn().setBody(requestList);
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
                responseList.forEach((responseItem) -> {
                    responseObserver.onNext(responseItem);
                });
            } else {
                responseObserver.onNext(responseBody);
            }
            responseObserver.onCompleted();
        } catch (InterruptedException e) {
            responseObserver.onError(e);
        }
    }
}
