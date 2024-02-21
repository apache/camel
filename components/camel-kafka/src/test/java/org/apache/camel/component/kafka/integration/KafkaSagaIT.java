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
package org.apache.camel.component.kafka.integration;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.SagaCompletionMode;
import org.apache.camel.model.SagaPropagation;
import org.apache.camel.saga.InMemorySagaService;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.kafka.integration.common.TestProducerUtil.sendMessagesInRoute;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KafkaSagaIT extends BaseEmbeddedKafkaTestSupport {

    @Test
    public void testSaga() throws Exception {
        MockEndpoint result = contextExtension.getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        sendMessagesInRoute("direct:saga", 1, contextExtension.getProducerTemplate(), "Hello sag");

        result.assertIsSatisfied();
        assertTrue(SagaBean.isSame);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                getCamelContext().addService(new InMemorySagaService());

                from("direct:saga")
                        .saga()
                        .completionMode(SagaCompletionMode.MANUAL)
                        .bean(SagaBean.class, "checkId")
                        .to("kafka:saga");

                from("kafka:saga?autoOffsetReset=earliest&autoCommitIntervalMs=1000&pollTimeoutMs=1000&autoCommitEnable=true")
                        .saga()
                        .propagation(SagaPropagation.MANDATORY)
                        .bean(SagaBean.class, "checkId")
                        .to("mock:result")
                        .to("saga:complete");
            }
        };
    }
}

final class SagaBean {
    public static String id;
    public static Boolean isSame = false;

    private SagaBean() {
    }

    public static void checkId(Exchange exchange) {
        String sagaId = exchange.getIn().getHeader(Exchange.SAGA_LONG_RUNNING_ACTION, String.class);
        if (id == null) {
            id = sagaId;
        } else {
            isSame = id.equals(sagaId);
        }
    }
}
