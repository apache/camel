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
package org.apache.camel.component.jms.issues;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.apache.camel.component.jms.SerializableRequestDto;
import org.apache.camel.component.jms.SerializableResponseDto;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.apache.camel.test.infra.activemq.common.ConnectionFactoryHelper.createConnectionFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JmsInOutTransferExchangeInflightRepositoryFlushTest extends AbstractJMSTest {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory
                = createConnectionFactory(service);
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));
        return camelContext;
    }

    @Test
    public void testTransferExchangeInOut() throws Exception {
        assertEquals(0, context().getInflightRepository().size());

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        template.send("direct:start", exchange -> exchange.getIn().setBody(new SerializableRequestDto("Restless Camel")));

        assertMockEndpointsSatisfied();

        assertEquals(0, context().getInflightRepository().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to(ExchangePattern.InOut, "activemq:responseGenerator?transferExchange=true&requestTimeout=5000")
                        .to("mock:result");

                from("activemq:responseGenerator?transferExchange=true")
                        .process(exchange -> {
                            // there are 2 inflight (one for both routes)
                            assertEquals(2, exchange.getContext().getInflightRepository().size());
                            exchange.getIn().setBody(new SerializableResponseDto(true));
                        });
            }
        };
    }
}
