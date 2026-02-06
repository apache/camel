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

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;

@TestMethodOrder(MethodOrderer.MethodName.class)
public class KafkaProducerMultipleMessagesInTransactionWithLoopTest extends CamelTestSupport {

    @EndpointInject("mock:done")
    protected MockEndpoint doneEndpoint;

    private MockProducer<String, String> mockProducer
            = new MockProducer<>(true, new StringSerializer(), new StringSerializer());

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        KafkaClientFactory kcf = Mockito.mock(KafkaClientFactory.class);
        Mockito.when(kcf.getProducer(any(Properties.class))).thenReturn(mockProducer);

        KafkaComponent kafka = new KafkaComponent();
        kafka.getConfiguration().setBrokers("broker1:1234,broker2:4567");
        kafka.getConfiguration().setRecordMetadata(true);
        kafka.setKafkaClientFactory(kcf);

        context.addComponent("kafka", kafka);

        return context;
    }

    /**
     * In a Loop EIP sends messages with transactional.id to Kafka.
     */
    @Test
    public void test01_HappyLoopPath() throws Exception {
        int messageCount = 5;

        doneEndpoint.expectedMessageCount(1);

        template.sendBody("direct:loop", messageCount);

        MockEndpoint.assertIsSatisfied(context);

        assertEquals(messageCount, mockProducer.history().size());
        assertEquals(1, mockProducer.commitCount());
    }

    /**
     * Using the same route as in test01_HappyLoopPath but will throw a RuntimeException on the last iteration.
     */
    @Test
    public void test02_OnExceptionWithLoop() throws Exception {
        Exception exceptionCaught = null;
        int throwExceptionOnIndex = 4;
        try {
            template.sendBodyAndHeader("direct:loop", throwExceptionOnIndex + 1, "ThrowExceptionOnIndex",
                    throwExceptionOnIndex);
        } catch (CamelExecutionException e) {
            exceptionCaught = e;
        }

        assertInstanceOf(CamelExecutionException.class, exceptionCaught);
        assertInstanceOf(RuntimeException.class, exceptionCaught.getCause());
        assertEquals("Failing with Index: " + throwExceptionOnIndex, exceptionCaught.getCause().getMessage());

        assertEquals(0, mockProducer.history().size());
        assertEquals(0, mockProducer.commitCount());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:loop")
                        .id("loop")
                        .setVariable("ThrowExceptionOnIndex",
                                header("ThrowExceptionOnIndex").convertTo(Integer.class))
                        .loop(body().convertTo(Integer.class))
                        .choice().when(exchange -> {
                            Integer throwExceptionOnIndex = exchange.getVariable("ThrowExceptionOnIndex", Integer.class);
                            Integer camelLoopIndex = exchange.getProperty("CamelLoopIndex", Integer.class);

                            if (null != throwExceptionOnIndex && throwExceptionOnIndex.equals(camelLoopIndex)) {
                                return true;
                            } else {
                                log.info("***** Sending message to Kafka from Loop exchange with id '{}' and UnitOfWork: {}",
                                        exchange.getExchangeId(), exchange.getUnitOfWork().hashCode());

                                return false;
                            }
                        })
                        .throwException(RuntimeException.class, "Failing with Index: ${exchangeProperty.CamelLoopIndex}")
                        .otherwise()
                        .setBody(simple("test ${exchangeProperty.CamelLoopIndex}"))
                        .to("kafka:loop?additional-properties[transactional.id]=1234&additional-properties[enable.idempotence]=true&additional-properties[retries]=5")
                        .end() // .choice
                        .end() // .loop
                        .to("mock:done");
            }
        };
    }
}
