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
import org.apache.camel.component.grpc.GrpcConstants;
import org.apache.camel.component.grpc.GrpcUtils;

/**
 * An exchange forwarder that forwards each Camel exchange in the same request channel.
 */
class GrpcStreamingExchangeForwarder implements GrpcExchangeForwarder {

    private final GrpcConfiguration configuration;

    private final Object grpcStub;

    private volatile StreamObserver<Object> currentStream;

    private volatile StreamObserver<Object> currentResponseObserver;

    public GrpcStreamingExchangeForwarder(GrpcConfiguration configuration, Object grpcStub) {
        this.configuration = configuration;
        this.grpcStub = grpcStub;
    }

    @Override
    public boolean forward(Exchange exchange, StreamObserver<Object> responseObserver, AsyncCallback callback) {
        Message message = exchange.getIn();
        StreamObserver<Object> streamObserver = checkAndRecreateStreamObserver(responseObserver);
        if (message.getHeaders().containsKey(GrpcConstants.GRPC_EVENT_TYPE_HEADER)) {
            switch (message.getHeader(GrpcConstants.GRPC_EVENT_TYPE_HEADER, String.class)) {
                case GrpcConstants.GRPC_EVENT_TYPE_ON_NEXT -> streamObserver.onNext(message.getBody());
                case GrpcConstants.GRPC_EVENT_TYPE_ON_ERROR -> streamObserver.onError((Throwable) message.getBody());
                case GrpcConstants.GRPC_EVENT_TYPE_ON_COMPLETED -> streamObserver.onCompleted();
            }
        } else {
            streamObserver.onNext(message.getBody());
        }
        callback.done(true);
        return true;
    }

    @Override
    public void forward(Exchange exchange) {
        throw new UnsupportedOperationException("Synchronous call is not supported in streaming mode");
    }

    @Override
    public void shutdown() {
        if (this.currentResponseObserver != null) {
            checkAndRecreateStreamObserver(this.currentResponseObserver).onCompleted();
        }
        doCloseStream();
    }

    private StreamObserver<Object> checkAndRecreateStreamObserver(StreamObserver<Object> responseObserver) {
        StreamObserver<Object> curStream = this.currentStream;
        if (curStream == null) {
            synchronized (this) {
                if (this.currentStream == null) {
                    this.currentResponseObserver = responseObserver;
                    this.currentStream = doCreateStream(responseObserver);
                }

                curStream = this.currentStream;
            }
        }

        StreamObserver<Object> curResponseObserver = this.currentResponseObserver;
        if (curResponseObserver != null && !curResponseObserver.equals(responseObserver)) {
            throw new IllegalArgumentException("This forwarder must always use the same response observer");
        }
        return curStream;
    }

    private void doCloseStream() {
        synchronized (this) {
            this.currentStream = null;
            this.currentResponseObserver = null;
        }
    }

    private StreamObserver<Object> doCreateStream(StreamObserver<Object> streamObserver) {

        return GrpcUtils.invokeAsyncMethodStreaming(grpcStub, configuration.getMethod(), new StreamObserver<Object>() {

            @Override
            public void onNext(Object o) {
                streamObserver.onNext(o);

            }

            @Override
            public void onError(Throwable throwable) {
                doCloseStream();
                streamObserver.onError(throwable);
            }

            @Override
            public void onCompleted() {
                doCloseStream();
                streamObserver.onCompleted();
            }

        });
    }

}
