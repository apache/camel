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

import io.grpc.stub.StreamObserver;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.AsyncProducerCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Producer;
import org.apache.camel.component.grpc.GrpcConfiguration;
import org.apache.camel.component.grpc.GrpcConstants;
import org.apache.camel.impl.EmptyProducerCache;
import org.apache.camel.impl.ProducerCache;
import org.apache.camel.util.CamelContextHelper;

/**
 * A stream observer that routes all responses to another endpoint.
 */
public class GrpcResponseRouterStreamObserver implements StreamObserver<Object> {

    private final Endpoint sourceEndpoint;
    private final GrpcConfiguration configuration;
    private final Endpoint endpoint;
    private final ProducerCache producerCache;

    public GrpcResponseRouterStreamObserver(GrpcConfiguration configuration, Endpoint sourceEndpoint) {
        this.configuration = configuration;
        this.sourceEndpoint = sourceEndpoint;
        this.endpoint = CamelContextHelper.getMandatoryEndpoint(sourceEndpoint.getCamelContext(), configuration.getStreamRepliesTo());
        this.producerCache = new EmptyProducerCache(this, sourceEndpoint.getCamelContext());
    }

    @Override
    public void onNext(Object o) {
        Exchange exchange = sourceEndpoint.createExchange();
        exchange.getIn().setHeader(GrpcConstants.GRPC_EVENT_TYPE_HEADER, GrpcConstants.GRPC_EVENT_TYPE_ON_NEXT);
        exchange.getIn().setBody(o);
        doSend(exchange, done -> {
        });

    }

    @Override
    public void onError(Throwable throwable) {
        if (configuration.isForwardOnError()) {
            Exchange exchange = sourceEndpoint.createExchange();
            exchange.getIn().setHeader(GrpcConstants.GRPC_EVENT_TYPE_HEADER, GrpcConstants.GRPC_EVENT_TYPE_ON_ERROR);
            exchange.getIn().setBody(throwable);
            doSend(exchange, done -> {
            });
        }
    }

    @Override
    public void onCompleted() {
        if (configuration.isForwardOnCompleted()) {
            Exchange exchange = sourceEndpoint.createExchange();
            exchange.getIn().setHeader(GrpcConstants.GRPC_EVENT_TYPE_HEADER, GrpcConstants.GRPC_EVENT_TYPE_ON_COMPLETED);
            doSend(exchange, done -> {
            });
        }
    }


    private void doSend(Exchange ex, AsyncCallback callback) {
        producerCache.doInAsyncProducer(endpoint, ex, ExchangePattern.InOnly, callback, new AsyncProducerCallback() {
            @Override
            public boolean doInAsyncProducer(Producer producer, AsyncProcessor asyncProducer, Exchange exchange2, ExchangePattern exchangePattern, AsyncCallback callback2) {
                return asyncProducer.process(exchange2, callback2);
            }
        });
    }

}
