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

import java.util.function.Predicate;

import org.apache.camel.component.kafka.SeekPolicy;
import org.apache.camel.component.kafka.consumer.support.ProcessingResult;
import org.apache.camel.resume.ConsumerListener;
import org.apache.kafka.clients.consumer.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaConsumerListener implements ConsumerListener<Object, ProcessingResult> {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumerListener.class);
    private Consumer<?, ?> consumer;
    private SeekPolicy seekPolicy;

    private Predicate<?> afterConsumeEval;

    public Consumer<?, ?> getConsumer() {
        return consumer;
    }

    public void setConsumer(Consumer<?, ?> consumer) {
        this.consumer = consumer;
    }

    public SeekPolicy getSeekPolicy() {
        return seekPolicy;
    }

    public void setSeekPolicy(SeekPolicy seekPolicy) {
        this.seekPolicy = seekPolicy;
    }

    @Override
    public void setResumableCheck(Predicate<?> afterConsumeEval) {
        this.afterConsumeEval = afterConsumeEval;
    }

    @Override
    public boolean afterConsume(@SuppressWarnings("unused") Object ignored) {
        if (afterConsumeEval.test(null)) {
            LOG.warn("State changed, therefore resuming the consumer");
            consumer.resume(consumer.assignment());

            return true;
        }

        LOG.warn("The consumer is not yet resumable");
        return false;
    }

    @Override
    public boolean afterProcess(ProcessingResult result) {
        if (result.isFailed()) {
            LOG.warn("Pausing consumer due to error on the last processing");
            consumer.pause(consumer.assignment());

            if (seekPolicy == SeekPolicy.BEGINNING) {
                LOG.debug("Seeking from the beginning of topic");
                consumer.seekToBeginning(consumer.assignment());
            } else if (seekPolicy == SeekPolicy.END) {
                LOG.debug("Seeking from the end off the topic");
                consumer.seekToEnd(consumer.assignment());
            }

            return false;
        }

        return true;
    }
}
