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
package org.apache.camel.component.sjms2.batch;

import java.util.List;

import jakarta.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms2.Sjms2Component;
import org.apache.camel.component.sjms2.support.Jms2TestSupport;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InOnlyTopicDurableBatchConsumerTest extends Jms2TestSupport {

    private static final String CONNECTION_ID = "test-connection-1";
    @RegisterExtension
    public static ArtemisService service = ArtemisServiceFactory.createTCPAllProtocolsService();

    @Test
    public void testDurableTopic() throws Exception {
        final String expectedBody = "Hello World!";
        MockEndpoint mock1 = getMockEndpoint("mock:result");
        mock1.expectedMessageCount(2);

        MockEndpoint mock2 = getMockEndpoint("mock:result2");
        mock2.expectedMessageCount(1);

        for (int i = 0; i < 8; i++) {
            template.sendBody("sjms2:topic:foo", expectedBody);
        }

        MockEndpoint.assertIsSatisfied(context);

        for (MockEndpoint mock : List.of(mock1, mock2)) {
            int total = 0;
            for (Exchange exchange : mock.getExchanges()) {
                List<?> batch = exchange.getMessage().getBody(List.class);
                assertNotNull(batch);
                total = total + batch.size();
                assertTrue(batch.stream()
                        .map(Exchange.class::cast)
                        .map(e -> e.getMessage().getBody(String.class))
                        .allMatch(expectedBody::equals));
            }
            assertEquals(8, total);
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        Sjms2Component sjms = context.getComponent("sjms2", Sjms2Component.class);

        // need to use a pooled CF so we reuse same connection for multiple client connections
        JmsPoolConnectionFactory pcf = new JmsPoolConnectionFactory();
        pcf.setConnectionFactory(sjms.getConnectionFactory());
        sjms.setConnectionFactory(pcf);
        sjms.setClientId(CONNECTION_ID);

        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("sjms2:topic:foo?durableSubscriptionName=bar1&batching=true&batchSize=5")
                        .to("mock:result");

                from("sjms2:topic:foo?durableSubscriptionName=bar2&batching=true&batchSize=10")
                        .to("mock:result2");
            }
        };
    }

    protected ConnectionFactory getConnectionFactory() throws Exception {
        return getConnectionFactory(service.serviceAddress());
    }
}
