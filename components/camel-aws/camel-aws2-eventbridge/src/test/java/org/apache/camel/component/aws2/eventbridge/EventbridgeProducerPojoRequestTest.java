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
package org.apache.camel.component.aws2.eventbridge;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * When {@code pojoRequest=true}, the producer must fail fast if the body is not the expected request type, rather than
 * silently doing nothing (see CAMEL-24261).
 */
class EventbridgeProducerPojoRequestTest {

    @Test
    void putRuleWithPojoRequestAndWrongBodyTypeThrows() {
        EventbridgeConfiguration configuration = new EventbridgeConfiguration();
        configuration.setPojoRequest(true);
        configuration.setOperation(EventbridgeOperations.putRule);

        EventbridgeEndpoint endpoint = mock(EventbridgeEndpoint.class);
        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(endpoint.getEventbridgeClient()).thenReturn(mock(EventBridgeClient.class));

        EventbridgeProducer producer = new EventbridgeProducer(endpoint);

        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().setBody("not a PutRuleRequest");

        assertThatThrownBy(() -> producer.process(exchange))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("putRule operation requires PutRuleRequest in POJO mode");
    }
}
