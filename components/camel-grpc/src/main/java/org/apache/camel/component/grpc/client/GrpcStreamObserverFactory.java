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
import org.apache.camel.AsyncProducer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.grpc.GrpcConfiguration;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;

public class GrpcStreamObserverFactory extends ServiceSupport {

    private final Endpoint sourceEndpoint;
    private final GrpcConfiguration configuration;

    private Endpoint endpoint;
    private AsyncProducer producer;

    public GrpcStreamObserverFactory(Endpoint sourceEndpoint, GrpcConfiguration configuration) {
        this.sourceEndpoint = sourceEndpoint;
        this.configuration = configuration;
    }

    public StreamObserver<Object> getStreamObserver(Exchange exchange, AsyncCallback callback) {
        if (configuration.getStreamRepliesTo() == null) {
            return new GrpcResponseAggregationStreamObserver(exchange, callback);
        } else {
            return new GrpcResponseRouterStreamObserver(configuration, sourceEndpoint, producer, exchange, callback);
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (configuration.getStreamRepliesTo() != null) {
            this.endpoint = CamelContextHelper.getMandatoryEndpoint(
                    sourceEndpoint.getCamelContext(), configuration.getStreamRepliesTo());
            this.producer = endpoint.createAsyncProducer();
            ServiceHelper.startService(producer);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (configuration.getStreamRepliesTo() != null) {
            ServiceHelper.stopService(producer);
        }
    }
}
