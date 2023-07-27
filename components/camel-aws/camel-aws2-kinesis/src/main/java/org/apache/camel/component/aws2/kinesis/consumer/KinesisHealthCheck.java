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
package org.apache.camel.component.aws2.kinesis.consumer;

import java.util.Objects;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

import org.apache.camel.component.aws2.kinesis.Kinesis2Endpoint;
import software.amazon.awssdk.services.kinesis.model.ListStreamsRequest;

public class KinesisHealthCheck extends TimerTask {
    private Kinesis2Endpoint endpoint;
    private KinesisConnection kinesisConnection;

    public KinesisHealthCheck(Kinesis2Endpoint endpoint,
                              KinesisConnection kinesisConnection) {
        this.endpoint = endpoint;
        this.kinesisConnection = kinesisConnection;
    }

    @Override
    public void run() {
        if (this.endpoint.getConfiguration().isAsyncClient()) {
            try {
                if (Objects.isNull(kinesisConnection.getAsyncClient(this.endpoint)) ||
                        kinesisConnection.getAsyncClient(this.endpoint)
                                .listStreams(ListStreamsRequest
                                        .builder()
                                        .build())
                                .get()
                                .streamNames()
                                .isEmpty()) {
                    kinesisConnection.setKinesisAsyncClient(endpoint.getAsyncClient());
                }
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        } else {
            if (Objects.isNull(kinesisConnection.getClient(this.endpoint)) ||
                    kinesisConnection.getClient(this.endpoint)
                            .listStreams(ListStreamsRequest
                                    .builder()
                                    .build())
                            .streamNames()
                            .isEmpty()) {
                kinesisConnection.setKinesisClient(endpoint.getClient());
            }
        }
    }

}
