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

import org.apache.camel.component.aws2.kinesis.Kinesis2Endpoint;
import org.apache.camel.component.aws2.kinesis.client.KinesisClientFactory;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.awssdk.services.kinesis.KinesisClient;

public class KinesisConnection {

    private static KinesisConnection instance;
    private KinesisClient kinesisClient = null;
    private KinesisAsyncClient kinesisAsyncClient = null;

    private KinesisConnection() {
    }

    public static synchronized KinesisConnection getInstance() {
        if (instance == null) {
            synchronized (KinesisConnection.class) {
                if (instance == null) {
                    instance = new KinesisConnection();
                }
            }
        }
        return instance;
    }

    public KinesisClient getClient(final Kinesis2Endpoint endpoint) {
        if (Objects.isNull(kinesisClient)) {
            kinesisClient = endpoint.getConfiguration().getAmazonKinesisClient() != null
                    ? endpoint.getConfiguration().getAmazonKinesisClient()
                    : KinesisClientFactory.getKinesisClient(endpoint.getConfiguration()).getKinesisClient();
        }
        return kinesisClient;
    }

    public KinesisAsyncClient getAsyncClient(final Kinesis2Endpoint endpoint) {
        if (Objects.isNull(kinesisAsyncClient)) {
            kinesisAsyncClient = KinesisClientFactory
                    .getKinesisAsyncClient(endpoint.getConfiguration())
                    .getKinesisAsyncClient();
        }
        return kinesisAsyncClient;
    }

    public void setKinesisClient(KinesisClient kinesisClient) {
        this.kinesisClient = kinesisClient;
    }

    public void setKinesisAsyncClient(KinesisAsyncClient kinesisAsyncClient) {
        this.kinesisAsyncClient = kinesisAsyncClient;
    }
}
