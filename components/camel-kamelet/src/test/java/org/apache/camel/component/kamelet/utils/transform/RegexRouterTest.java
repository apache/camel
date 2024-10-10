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
package org.apache.camel.component.kamelet.utils.transform;

import org.apache.camel.Exchange;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegexRouterTest {

    private DefaultCamelContext camelContext;

    private RegexRouter processor;

    private final String topic = "hello";

    @BeforeEach
    void setup() {
        camelContext = new DefaultCamelContext();
        processor = new RegexRouter();
    }

    @Test
    void shouldReplaceFieldToPlainJson() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setHeader(KafkaConstants.TOPIC, topic);

        processor.process(".*ll.*", "newTopic", exchange);

        Assertions.assertEquals("newTopic", exchange.getMessage().getHeader(KafkaConstants.OVERRIDE_TOPIC));
    }
}
