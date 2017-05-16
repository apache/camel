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
package org.apache.camel.component.grpc.client;

import java.util.LinkedList;
import java.util.List;

import io.grpc.stub.StreamObserver;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;

/*
 * gRPC response stream observer which is collecting response objects every
 * onNext() call into the list and setting them inside Body when onCompleted() invoked
 */
public class GrpcResponseAggregationStreamObserver implements StreamObserver<Object> {
    private final Exchange exchange;
    private final AsyncCallback callback;
    private List<Object> responseCollection = new LinkedList<Object>();
    
    public GrpcResponseAggregationStreamObserver(Exchange exchange, AsyncCallback callback) {
        this.exchange = exchange;
        this.callback = callback;
    }

    @Override
    public void onNext(Object response) {
        responseCollection.add(response);
    }

    @Override
    public void onError(Throwable throwable) {
        exchange.setException(throwable);
        callback.done(false);
    }

    @Override
    public void onCompleted() {
        exchange.getOut().setHeaders(exchange.getIn().getHeaders());
        exchange.getOut().setBody(responseCollection);
        callback.done(false);
    }

}
