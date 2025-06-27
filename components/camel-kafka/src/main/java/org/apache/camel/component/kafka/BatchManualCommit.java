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
package org.apache.camel.component.kafka;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchManualCommit implements Processor {

    private static final Logger LOG = LoggerFactory.getLogger(BatchManualCommit.class);

    @Override
    public void process(Exchange exchange) throws Exception {
        KafkaManualCommit manual
                = exchange.getMessage().getHeader(KafkaConstants.MANUAL_COMMIT, KafkaManualCommit.class);
        if (manual == null) {
            List<?> exchanges = exchange.getMessage().getBody(List.class);
            if (exchanges != null && !exchanges.isEmpty()) {
                Object obj = exchanges.get(exchanges.size() - 1);
                if (obj instanceof Exchange last) {
                    manual = last.getMessage().getHeader(KafkaConstants.MANUAL_COMMIT, KafkaManualCommit.class);
                }
            }
        }

        if (manual != null) {
            LOG.debug("Performing Kafka Batch manual commit: {}", manual);
            manual.commit();
        } else {
            LOG.debug("Cannot perform Kafka Batch manual commit due header: {} is missing", KafkaConstants.MANUAL_COMMIT);
        }
    }
}
