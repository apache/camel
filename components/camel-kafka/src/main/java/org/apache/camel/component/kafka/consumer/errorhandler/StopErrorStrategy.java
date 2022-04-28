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

package org.apache.camel.component.kafka.consumer.errorhandler;

import org.apache.camel.component.kafka.KafkaFetchRecords;
import org.apache.camel.component.kafka.PollExceptionStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StopErrorStrategy implements PollExceptionStrategy {
    private static final Logger LOG = LoggerFactory.getLogger(StopErrorStrategy.class);
    private final KafkaFetchRecords recordFetcher;
    private boolean retry = true;

    public StopErrorStrategy(KafkaFetchRecords recordFetcher) {
        this.recordFetcher = recordFetcher;
    }

    @Override
    public void reset() {
        retry = true;
    }

    @Override
    public boolean canContinue() {
        return retry;
    }

    @Override
    public void handle(long partitionLastOffset, Exception exception) {
        // stop and terminate consumer
        LOG.warn("Requesting the consumer to stop based on polling exception strategy");

        retry = false;
        recordFetcher.setConnected(false);
    }
}
