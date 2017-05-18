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

import java.util.Map;

import io.grpc.stub.StreamObserver;
import org.apache.camel.Exchange;
import org.apache.camel.component.grpc.GrpcConsumer;
import org.apache.camel.component.grpc.GrpcEndpoint;

/**
 * gRPC request abstract stream observer is the base class for other stream
 * observer implementations
 */
public abstract class GrpcRequestAbstractStreamObserver implements StreamObserver<Object> {
    protected final GrpcEndpoint endpoint;
    protected final GrpcConsumer consumer;
    protected Exchange exchange;
    protected StreamObserver<Object> responseObserver;
    protected Map<String, Object> headers;

    public GrpcRequestAbstractStreamObserver(GrpcEndpoint endpoint, GrpcConsumer consumer, StreamObserver<Object> responseObserver, Map<String, Object> headers) {
        this.endpoint = endpoint;
        this.consumer = consumer;
        this.responseObserver = responseObserver;
        this.headers = headers;
    }
}