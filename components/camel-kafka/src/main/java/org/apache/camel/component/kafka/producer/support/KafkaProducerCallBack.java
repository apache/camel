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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.LongAdder;

import org.apache.camel.AsyncCallback;
import org.apache.camel.util.ObjectHelper;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.kafka.producer.support.ProducerUtil.setException;
import static org.apache.camel.component.kafka.producer.support.ProducerUtil.setRecordMetadata;

public final class KafkaProducerCallBack implements Callback {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaProducerCallBack.class);

    private final Object body;
    private final AsyncCallback callback;
    private final LongAdder count = new LongAdder();
    private final ExecutorService workerPool;
    private final boolean record;
    private final List<RecordMetadata> recordMetadataList = new ArrayList<>();

    public KafkaProducerCallBack(Object body, AsyncCallback callback, ExecutorService workerPool,
                                 boolean record) {
        this.body = body;
        this.callback = callback;
        // The worker pool should be created for both sync and async modes, so checking it
        // is merely a safeguard
        this.workerPool = ObjectHelper.notNull(workerPool, "workerPool");
        this.record = record;
        count.increment();

        if (record) {
            setRecordMetadata(body, recordMetadataList);
        }
    }

    public void increment() {
        count.increment();
    }

    public boolean allSent() {
        count.decrement();
        if (count.intValue() == 0) {
            LOG.trace("All messages sent, continue routing.");
            // was able to get all the work done while queuing the requests
            callback.done(true);

            return true;
        }

        return false;
    }

    @Override
    public void onCompletion(RecordMetadata recordMetadata, Exception e) {
        setException(body, e);

        if (record) {
            recordMetadataList.add(recordMetadata);
        }

        count.decrement();
        if (count.intValue() == 0) {
            // use worker pool to continue routing the exchange
            // as this thread is from Kafka Callback and should not be used
            // by Camel routing
            workerPool.submit(this::doContinueRouting);
        }
    }

    private void doContinueRouting() {
        LOG.trace("All messages sent, continue routing (within thread).");
        callback.done(false);
    }

}
