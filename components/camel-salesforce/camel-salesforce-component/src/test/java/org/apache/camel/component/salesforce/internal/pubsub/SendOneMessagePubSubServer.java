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
package org.apache.camel.component.salesforce.internal.pubsub;

import java.util.Timer;
import java.util.TimerTask;

import com.google.protobuf.ByteString;
import com.salesforce.eventbus.protobuf.FetchRequest;
import com.salesforce.eventbus.protobuf.FetchResponse;
import com.salesforce.eventbus.protobuf.PubSubGrpc;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import static org.apache.camel.component.salesforce.internal.client.PubSubApiClient.PUBSUB_ERROR_AUTH_ERROR;

public class SendOneMessagePubSubServer extends PubSubGrpc.PubSubImplBase {

    public int onNextCalls = 0;

    @Override
    public StreamObserver<FetchRequest> subscribe(StreamObserver<FetchResponse> client) {

        return new StreamObserver<>() {
            @Override
            public void onNext(FetchRequest request) {
                onNextCalls = onNextCalls + 1;
                if (onNextCalls > 1) {
                    TimerTask task = new TimerTask() {
                        public void run() {
                            StatusRuntimeException e = new StatusRuntimeException(Status.UNAUTHENTICATED, new Metadata());
                            e.getTrailers().put(Metadata.Key.of("error-code", Metadata.ASCII_STRING_MARSHALLER),
                                    PUBSUB_ERROR_AUTH_ERROR);
                            client.onError(e);
                        }
                    };
                    Timer timer = new Timer("Timer");
                    long delay = 1000L;
                    timer.schedule(task, delay);
                    return;
                }
                TimerTask task = new TimerTask() {
                    public void run() {
                        FetchResponse response = FetchResponse.newBuilder()
                                .setLatestReplayId(ByteString.copyFromUtf8("123"))
                                .build();
                        client.onNext(response);
                    }
                };
                Timer timer = new Timer("Timer");
                long delay = 1000L;
                timer.schedule(task, delay);
            }

            @Override
            public void onError(Throwable t) {

            }

            @Override
            public void onCompleted() {

            }
        };
    }
}
