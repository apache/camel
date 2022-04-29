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
import org.apache.camel.component.kafka.KafkaConsumer;
import org.apache.camel.component.kafka.SeekPolicy;
import org.apache.camel.resume.ResumeStrategy;
import org.apache.camel.spi.StateRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ResumeStrategyFactory {

    /**
     * A NO-OP resume strategy that does nothing (i.e.: no resume)
     */
    private static class NoOpKafkaConsumerResumeAdapter implements KafkaConsumerResumeAdapter {

        @SuppressWarnings("unused")
        @Override
        public void setConsumer(Consumer<?, ?> consumer) {
            // NO-OP
        }

        @Override
        public void setKafkaResumable(KafkaResumable kafkaResumable) {
            // NO-OP
        }

        @SuppressWarnings("unused")
        @Override
        public void resume() {

        }
    }

    private static final NoOpKafkaConsumerResumeAdapter NO_OP_RESUME_STRATEGY = new NoOpKafkaConsumerResumeAdapter();
    private static final Logger LOG = LoggerFactory.getLogger(ResumeStrategyFactory.class);

    private ResumeStrategyFactory() {
    }

    public static KafkaConsumerResumeAdapter resolveResumeAdapter(KafkaConsumer kafkaConsumer) {
        // When using resumable routes, which register the strategy via service, it takes priority over everything else
        ResumeStrategy resumeStrategy = kafkaConsumer.getResumeStrategy();
        if (resumeStrategy != null) {
            KafkaConsumerResumeAdapter adapter = resumeStrategy.getAdapter(KafkaConsumerResumeAdapter.class);

            // The strategy should not be able to be created without an adapter, but let's be safe
            assert adapter != null;

            return adapter;
        }

        KafkaConfiguration configuration = kafkaConsumer.getEndpoint().getConfiguration();

        return resolveBuiltinResumeAdapters(configuration);
    }

    private static KafkaConsumerResumeAdapter resolveBuiltinResumeAdapters(KafkaConfiguration configuration) {
        LOG.debug("No resume strategy was provided ... checking for built-ins ...");
        StateRepository<String, String> offsetRepository = configuration.getOffsetRepository();
        SeekPolicy seekTo = configuration.getSeekTo();

        if (offsetRepository != null) {
            LOG.info("Using resume from offset strategy");
            return new OffsetKafkaConsumerResumeAdapter(offsetRepository);
        } else if (seekTo != null) {
            LOG.info("Using resume from seek policy strategy with seeking from {}", seekTo);
            return new SeekPolicyKafkaConsumerResumeAdapter(seekTo);
        }

        LOG.info("Using NO-OP resume strategy");
        return NO_OP_RESUME_STRATEGY;
    }
}
