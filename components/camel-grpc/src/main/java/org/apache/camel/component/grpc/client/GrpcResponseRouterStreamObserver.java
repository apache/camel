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

import java.util.Objects;

import io.grpc.stub.StreamObserver;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProducer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.grpc.GrpcConfiguration;
import org.apache.camel.component.grpc.GrpcConstants;

/**
 * A stream observer that routes all responses to another endpoint.
 */
public class GrpcResponseRouterStreamObserver implements StreamObserver<Object> {

    private final Endpoint sourceEndpoint;
    private final GrpcConfiguration configuration;
    private final AsyncProducer producer;
    private final Exchange exchange;
    private final AsyncCallback callback;

    public GrpcResponseRouterStreamObserver(GrpcConfiguration configuration,
                                            Endpoint sourceEndpoint,
                                            AsyncProducer producer,
                                            Exchange exchange,
                                            AsyncCallback callback) {
        this.configuration = configuration;
        this.sourceEndpoint = sourceEndpoint;
        this.producer = producer;
        this.exchange = exchange;
        this.callback = callback;
    }

    @Override
    public void onNext(Object o) {
        Exchange newExchange = sourceEndpoint.createExchange();
        inherit(newExchange);
        newExchange.getIn().setHeader(GrpcConstants.GRPC_EVENT_TYPE_HEADER, GrpcConstants.GRPC_EVENT_TYPE_ON_NEXT);
        newExchange.getIn().setBody(o);
        doSend(newExchange);
    }

    @Override
    public void onError(Throwable throwable) {
        if (configuration.isForwardOnError()) {
            Exchange newExchange = sourceEndpoint.createExchange();
            inherit(newExchange);
            newExchange.getIn().setHeader(GrpcConstants.GRPC_EVENT_TYPE_HEADER, GrpcConstants.GRPC_EVENT_TYPE_ON_ERROR);
            newExchange.getIn().setBody(throwable);
            doSend(newExchange);
        }
        callback.done(true);
    }

    @Override
    public void onCompleted() {
        if (configuration.isForwardOnCompleted()) {
            Exchange newExchange = sourceEndpoint.createExchange();
            inherit(newExchange);
            newExchange.getIn().setHeader(GrpcConstants.GRPC_EVENT_TYPE_HEADER, GrpcConstants.GRPC_EVENT_TYPE_ON_COMPLETED);
            doSend(newExchange);
        }
        callback.done(true);
    }

    private void doSend(Exchange newExchange) {
        producer.processAsync(newExchange);
    }

    private void inherit(Exchange newExchange) {
        if (configuration.isInheritExchangePropertiesForReplies()) {
            for (var entry : exchange.getProperties().entrySet()) {
                newExchange.setProperty(entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        GrpcResponseRouterStreamObserver that = (GrpcResponseRouterStreamObserver) o;
        return Objects.equals(sourceEndpoint, that.sourceEndpoint) && Objects.equals(producer, that.producer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceEndpoint, producer);
    }
}
