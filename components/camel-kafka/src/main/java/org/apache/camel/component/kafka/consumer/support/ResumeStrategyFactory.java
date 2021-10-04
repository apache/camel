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

import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.spi.StateRepository;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ResumeStrategyFactory {
    /**
     * A NO-OP resume strategy that does nothing (i.e.: no resume)
     */
    private static class NoOpKafkaConsumerResumeStrategy implements KafkaConsumerResumeStrategy {
        @SuppressWarnings("unused")
        @Override
        public void resume(KafkaConsumer<?, ?> consumer) {
            // NO-OP
        }
    }

    private static final NoOpKafkaConsumerResumeStrategy NO_OP_RESUME_STRATEGY = new NoOpKafkaConsumerResumeStrategy();
    private static final Logger LOG = LoggerFactory.getLogger(ResumeStrategyFactory.class);

    private ResumeStrategyFactory() {
    }

    public static KafkaConsumerResumeStrategy newResumeStrategy(KafkaConfiguration configuration) {

        if (configuration.getResumeStrategy() != null) {
            return configuration.getResumeStrategy();
        }

        return builtinResumeStrategies(configuration);
    }

    private static KafkaConsumerResumeStrategy builtinResumeStrategies(KafkaConfiguration configuration) {
        StateRepository<String, String> offsetRepository = configuration.getOffsetRepository();
        String seekTo = configuration.getSeekTo();

        if (offsetRepository != null) {
            LOG.info("Using resume from offset strategy");
            return new OffsetKafkaConsumerResumeStrategy(offsetRepository);
        } else if (seekTo != null) {
            LOG.info("Using resume from seek policy strategy with seeking from {}", seekTo);
            return new SeekPolicyKafkaConsumerResumeStrategy(seekTo);
        }

        LOG.info("Using NO-OP resume strategy");
        return NO_OP_RESUME_STRATEGY;
    }
}
