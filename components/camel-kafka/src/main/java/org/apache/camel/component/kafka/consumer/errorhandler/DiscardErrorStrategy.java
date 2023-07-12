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

import org.apache.camel.component.kafka.PollExceptionStrategy;
import org.apache.kafka.clients.consumer.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiscardErrorStrategy implements PollExceptionStrategy {
    private static final Logger LOG = LoggerFactory.getLogger(DiscardErrorStrategy.class);
    private final Consumer<?, ?> consumer;

    public DiscardErrorStrategy(Consumer<?, ?> consumer) {
        this.consumer = consumer;
    }

    @Override
    public boolean canContinue() {
        return true;
    }

    @Override
    public void handle(long partitionLastOffset, Exception exception) {
        LOG.warn("Requesting the consumer to discard the message and continue to the next based on polling exception strategy");

        // skip this poison message and seek to the next message
        SeekUtil.seekToNextOffset(consumer, partitionLastOffset);
    }
}
