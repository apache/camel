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
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BridgeErrorStrategy implements PollExceptionStrategy {
    private static final Logger LOG = LoggerFactory.getLogger(BridgeErrorStrategy.class);
    private final KafkaFetchRecords recordFetcher;
    private final Consumer<?, ?> consumer;
    private boolean continueFlag = true; // whether to continue polling or not

    public BridgeErrorStrategy(KafkaFetchRecords recordFetcher, Consumer<?, ?> consumer) {
        this.recordFetcher = recordFetcher;
        this.consumer = consumer;
    }

    @Override
    public boolean canContinue() {
        return continueFlag;
    }

    @Override
    public void handle(long partitionLastOffset, Exception exception) {
        LOG.warn("Deferring processing to the exception handler based on polling exception strategy");

        // use bridge error handler to route with exception
        recordFetcher.getBridge().handleException(exception);
        // skip this poison message and seek to the next message
        SeekUtil.seekToNextOffset(consumer, partitionLastOffset);

        if (exception instanceof AuthenticationException || exception instanceof AuthorizationException) {
            continueFlag = false;
        }
    }
}
