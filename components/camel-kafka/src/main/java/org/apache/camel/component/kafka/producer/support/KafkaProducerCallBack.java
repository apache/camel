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

package org.apache.camel.component.kafka.producer.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaProducerCallBack implements Callback {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaProducerCallBack.class);

    private final Object body;
    private final AsyncCallback callback;
    private final AtomicInteger count = new AtomicInteger(1);
    private final List<RecordMetadata> recordMetadatas = new ArrayList<>();
    private final ExecutorService workerPool;

    public KafkaProducerCallBack(Object body, ExecutorService workerPool, KafkaConfiguration configuration) {
        this(body, null, workerPool, configuration);
    }

    public KafkaProducerCallBack(Object body, AsyncCallback callback, ExecutorService workerPool,
                                 KafkaConfiguration configuration) {
        this.body = body;
        this.callback = callback;
        this.workerPool = Objects.requireNonNull(workerPool, "A worker pool must be provided");

        if (configuration.isRecordMetadata()) {
            if (body instanceof Exchange) {
                Exchange ex = (Exchange) body;
                ex.getMessage().setHeader(KafkaConstants.KAFKA_RECORDMETA, recordMetadatas);
            }
            if (body instanceof Message) {
                Message msg = (Message) body;
                msg.setHeader(KafkaConstants.KAFKA_RECORDMETA, recordMetadatas);
            }
        }
    }

    public void increment() {
        count.incrementAndGet();
    }

    public boolean allSent() {
        if (count.decrementAndGet() == 0) {
            LOG.trace("All messages sent, continue routing.");
            // was able to get all the work done while queuing the requests
            if (callback != null) {
                callback.done(true);
            }
            return true;
        }
        return false;
    }

    @Override
    public void onCompletion(RecordMetadata recordMetadata, Exception e) {
        if (e != null) {
            if (body instanceof Exchange) {
                ((Exchange) body).setException(e);
            }
            if (body instanceof Message && ((Message) body).getExchange() != null) {
                ((Message) body).getExchange().setException(e);
            }
        }

        recordMetadatas.add(recordMetadata);

        if (count.decrementAndGet() == 0) {
            // use worker pool to continue routing the exchange
            // as this thread is from Kafka Callback and should not be used
            // by Camel routing
            workerPool.submit(new Runnable() {
                @Override
                public void run() {
                    LOG.trace("All messages sent, continue routing.");
                    if (callback != null) {
                        callback.done(false);
                    }
                }
            });
        }
    }
}
