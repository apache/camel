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
package org.apache.camel.component.kafka.consumer.support;

import org.apache.kafka.clients.consumer.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A resume strategy that uses Camel's seekTo configuration for resuming
 */
public class SeekPolicyKafkaConsumerResumeStrategy implements KafkaConsumerResumeStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(SeekPolicyKafkaConsumerResumeStrategy.class);

    private final String seekPolicy;

    public SeekPolicyKafkaConsumerResumeStrategy(String seekPolicy) {
        this.seekPolicy = seekPolicy;
    }

    @Override
    public void resume(final Consumer<?, ?> consumer) {
        if (seekPolicy.equals("beginning")) {
            LOG.debug("Seeking from the beginning of topic");
            consumer.seekToBeginning(consumer.assignment());
        } else if (seekPolicy.equals("end")) {
            LOG.debug("Seeking from the end off the topic");
            consumer.seekToEnd(consumer.assignment());
        }
    }
}
