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

import static org.apache.camel.component.salesforce.internal.client.PubSubApiClient.PUBSUB_ERROR_CORRUPTED_REPLAY_ID;

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

public class SendInvalidReplayIdErrorPubSubServer extends PubSubGrpc.PubSubImplBase {

    private int count = 0;
    private int numberOfInvalidIdReplies;

    public SendInvalidReplayIdErrorPubSubServer(int numberOfInvalidIdReplies) {
        this.numberOfInvalidIdReplies = numberOfInvalidIdReplies;
    }

    @Override
    public StreamObserver<FetchRequest> subscribe(StreamObserver<FetchResponse> client) {

        return new StreamObserver<>() {
            @Override
            public void onNext(FetchRequest request) {
                count++;
                if (count <= numberOfInvalidIdReplies
                        && ByteString.copyFromUtf8("123").equals(request.getReplayId())) {
                    TimerTask task = new TimerTask() {
                        public void run() {
                            StatusRuntimeException e =
                                    new StatusRuntimeException(Status.UNAUTHENTICATED, new Metadata());
                            e.getTrailers()
                                    .put(
                                            Metadata.Key.of("error-code", Metadata.ASCII_STRING_MARSHALLER),
                                            PUBSUB_ERROR_CORRUPTED_REPLAY_ID);
                            client.onError(e);
                        }
                    };
                    schedule(task);
                    return;
                }
                TimerTask task = new TimerTask() {
                    public void run() {
                        FetchResponse response = FetchResponse.newBuilder()
                                .setLatestReplayId(ByteString.copyFromUtf8("456"))
                                .build();
                        client.onNext(response);
                    }
                };
                schedule(task);
            }

            private void schedule(TimerTask task) {
                Timer timer = new Timer("Timer");
                long delay = 1000L;
                timer.schedule(task, delay);
            }

            @Override
            public void onError(Throwable t) {}

            @Override
            public void onCompleted() {}
        };
    }
}
