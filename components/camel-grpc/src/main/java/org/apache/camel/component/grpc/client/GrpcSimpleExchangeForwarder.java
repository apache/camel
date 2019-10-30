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
package org.apache.camel.component.grpc.client;

import io.grpc.stub.StreamObserver;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.grpc.GrpcConfiguration;
import org.apache.camel.component.grpc.GrpcUtils;

/**
 * An exchange forwarder that creates a RPC request for each camel Exchange.
 */
class GrpcSimpleExchangeForwarder implements GrpcExchangeForwarder {

    private final GrpcConfiguration configuration;

    private final Object grpcStub;

    public GrpcSimpleExchangeForwarder(GrpcConfiguration configuration, Object grpcStub) {
        this.configuration = configuration;
        this.grpcStub = grpcStub;

    }

    @Override
    public boolean forward(Exchange exchange, StreamObserver<Object> responseObserver, AsyncCallback callback) {
        Message message = exchange.getIn();
        try {
            GrpcUtils.invokeAsyncMethod(grpcStub, configuration.getMethod(), message.getBody(), responseObserver);
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }
        return false;
    }

    @Override
    public void forward(Exchange exchange) {
        Message message = exchange.getIn();
        Object outBody = GrpcUtils.invokeSyncMethod(grpcStub, configuration.getMethod(), message.getBody());
        exchange.getOut().setHeaders(exchange.getIn().getHeaders());
        exchange.getOut().setBody(outBody);
    }

    @Override
    public void shutdown() {
    }
}
