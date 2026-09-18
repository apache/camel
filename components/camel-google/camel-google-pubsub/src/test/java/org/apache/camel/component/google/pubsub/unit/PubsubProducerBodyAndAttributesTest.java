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
package org.apache.camel.component.google.pubsub.unit;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.component.google.pubsub.GooglePubsubEndpoint;
import org.apache.camel.component.google.pubsub.GooglePubsubProducer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Verifies what the producer does with a list body that is neither a list of aggregated exchanges nor a payload.
 */
class PubsubProducerBodyAndAttributesTest {

    private final GooglePubsubEndpoint endpoint = mock();
    private final DefaultCamelContext context = new DefaultCamelContext();

    @AfterEach
    void tearDown() {
        context.stop();
    }

    @Test
    void aListMixingExchangesAndPayloadsIsRejected() {
        GooglePubsubProducer producer = new GooglePubsubProducer(endpoint);

        Exchange grouped = new DefaultExchange(context);
        grouped.getIn().setBody("an aggregated exchange");

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(List.of(grouped, "a plain payload"));

        // publishing only the exchange and dropping the payload without a word is worse than refusing the body
        assertThatThrownBy(() -> producer.process(exchange))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mixing 1 exchange(s) with 1 other element(s)");
    }
}
