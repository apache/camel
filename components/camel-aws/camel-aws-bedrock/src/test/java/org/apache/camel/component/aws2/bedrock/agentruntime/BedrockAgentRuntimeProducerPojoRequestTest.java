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
package org.apache.camel.component.aws2.bedrock.agentruntime;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * When {@code pojoRequest=true}, the producer must fail fast if the body is not the expected request type, rather than
 * silently doing nothing (see CAMEL-24261). The invoke and retrieve operations already did this; this covers the
 * remaining retrieveAndGenerate operation.
 */
class BedrockAgentRuntimeProducerPojoRequestTest {

    @Test
    void retrieveAndGenerateWithPojoRequestAndWrongBodyTypeThrows() throws Exception {
        BedrockAgentRuntimeConfiguration configuration = new BedrockAgentRuntimeConfiguration();
        configuration.setPojoRequest(true);
        configuration.setOperation(BedrockAgentRuntimeOperations.retrieveAndGenerate);

        BedrockAgentRuntimeEndpoint endpoint = mock(BedrockAgentRuntimeEndpoint.class);
        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(endpoint.getBedrockAgentRuntimeClient()).thenReturn(mock(BedrockAgentRuntimeClient.class));

        BedrockAgentRuntimeProducer producer = new BedrockAgentRuntimeProducer(endpoint);

        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().setBody("not the expected request type");

        assertThatThrownBy(() -> producer.process(exchange))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("retrieveAndGenerate operation requires a RetrieveAndGenerateRequest body when pojoRequest=true");
    }
}
