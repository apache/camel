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
package org.apache.camel.component.grpc;

import io.grpc.stub.StreamObserver;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;

public class GrpcProducerToRouteControlledStreamObserver extends DefaultProducer {
    public GrpcProducerToRouteControlledStreamObserver(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        StreamObserver<Object> observer = exchange.getProperty(
                GrpcConstants.GRPC_RESPONSE_OBSERVER, StreamObserver.class);
        String eventType = exchange.getMessage().getHeader(
                GrpcConstants.GRPC_EVENT_TYPE_HEADER, String.class);
        switch (eventType) {
            case GrpcConstants.GRPC_EVENT_TYPE_ON_NEXT -> observer.onNext(exchange.getMessage().getBody());
            case GrpcConstants.GRPC_EVENT_TYPE_ON_COMPLETED -> observer.onCompleted();
            case GrpcConstants.GRPC_EVENT_TYPE_ON_ERROR -> observer.onError((Throwable) exchange.getMessage().getBody());
        }
    }
}
