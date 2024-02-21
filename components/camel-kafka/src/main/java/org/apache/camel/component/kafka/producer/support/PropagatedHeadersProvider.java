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

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.KafkaProducer;
import org.apache.kafka.common.header.Header;

/**
 * Used to provide individual kafka header values if feature "batchWithIndividualHeaders" is enabled.
 */
public class PropagatedHeadersProvider {

    private final KafkaProducer kafkaProducer;
    private final Exchange parentExchange;
    private final Message parentMessage;

    // only set if batchWithIndividualHeaders is disabled (which is the default behaviour)
    private final List<Header> propagatedHeaders;

    public PropagatedHeadersProvider(KafkaProducer kafkaProducer, KafkaConfiguration configuration, Exchange parentExchange,
                                     Message parentMessage) {
        this.kafkaProducer = kafkaProducer;
        this.parentExchange = parentExchange;
        this.parentMessage = parentMessage;

        // extracting headers which need to be propagated: instant eval for common headers, lazy eval for individual headers
        propagatedHeaders = configuration.isBatchWithIndividualHeaders() ? null : getDefaultHeaders();
    }

    /**
     * Returns header values which are determined by parent exchange.
     */
    public final List<Header> getDefaultHeaders() {
        return kafkaProducer.getPropagatedHeaders(parentExchange, parentMessage);
    }

    /**
     * Create kafka header values by given Message.
     */
    public List<Header> getHeaders(Exchange childExchange, Message childMessage) {
        if (propagatedHeaders != null) {
            // default behaviour: use headers determined by parent Exchange
            return propagatedHeaders;
        } else {
            // parentExchange and childExchange may be identical, but parentMessage and childMessage are not.
            return kafkaProducer.getPropagatedHeaders(childExchange, childMessage);
        }
    }

}
