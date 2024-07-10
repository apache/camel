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

package org.apache.camel.component.kafka.consumer.devconsole;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.apache.camel.component.kafka.consumer.support.ProcessingResult;
import org.apache.kafka.clients.consumer.Consumer;

/**
 * A NO-OP collector that is used if the metrics collector is disabled
 */
public class NoopMetricsCollector implements DevConsoleMetricsCollector {
    @Override
    public void storeMetadata(Consumer<?, ?> consumer) {
        // NO-OP
    }

    @Override
    public void storeLastRecord(ProcessingResult result) {
        // NO-OP
    }

    @Override
    public void collectCommitMetrics(Consumer<?, ?> consumer) {
        // NO-OP
    }

    @Override
    public GroupMetadata getGroupMetadata() {
        return null;
    }

    @Override
    public KafkaTopicPosition getLastRecord() {
        return null;
    }

    @Override
    public String getThreadId() {
        return "";
    }

    @Override
    public List<KafkaTopicPosition> getCommitRecords() {
        return List.of();
    }

    @Override
    public CountDownLatch fetchCommitRecords() {
        return null;
    }
}
