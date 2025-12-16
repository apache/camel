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
package org.apache.camel.component.aws2.kinesis;

import java.io.Closeable;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.component.aws2.kinesis.client.KinesisClientFactory;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.awssdk.services.kinesis.KinesisClient;

/**
 * Holds connections to AWS from {@link KinesisClient} and {@link KinesisAsyncClient}.
 */
public class KinesisConnection implements Closeable {

    private KinesisClient kinesisClient;
    private KinesisAsyncClient kinesisAsyncClient;
    private final Lock lock = new ReentrantLock();

    public KinesisClient getClient(final Kinesis2Endpoint endpoint) {
        lock.lock();
        try {
            if (Objects.isNull(kinesisClient)) {
                kinesisClient = endpoint.getConfiguration().getAmazonKinesisClient() != null
                        ? endpoint.getConfiguration().getAmazonKinesisClient()
                        : KinesisClientFactory.getKinesisClient(endpoint.getConfiguration());
            }
            return kinesisClient;
        } finally {
            lock.unlock();
        }
    }

    public KinesisAsyncClient getAsyncClient(final Kinesis2Endpoint endpoint) {
        lock.lock();
        try {
            if (Objects.isNull(kinesisAsyncClient)) {
                kinesisAsyncClient = endpoint.getConfiguration().getAmazonKinesisAsyncClient() != null
                        ? endpoint.getConfiguration().getAmazonKinesisAsyncClient()
                        : KinesisClientFactory.getKinesisAsyncClient(endpoint.getConfiguration());
            }
            return kinesisAsyncClient;
        } finally {
            lock.unlock();
        }
    }

    public void setKinesisClient(final KinesisClient kinesisClient) {
        this.kinesisClient = kinesisClient;
    }

    public void setKinesisAsyncClient(final KinesisAsyncClient kinesisAsyncClient) {
        this.kinesisAsyncClient = kinesisAsyncClient;
    }

    @Override
    public void close() throws IOException {
        if (kinesisClient != null) {
            kinesisClient.close();
        }
        if (kinesisAsyncClient != null) {
            kinesisAsyncClient.close();
        }
    }
}
