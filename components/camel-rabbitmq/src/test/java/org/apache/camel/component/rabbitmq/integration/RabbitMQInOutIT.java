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
package org.apache.camel.component.rabbitmq.integration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.rabbitmq.RabbitMQConstants;
import org.apache.camel.component.rabbitmq.testbeans.TestNonSerializableObject;
import org.apache.camel.component.rabbitmq.testbeans.TestPartiallySerializableObject;
import org.apache.camel.component.rabbitmq.testbeans.TestSerializableObject;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.test.infra.rabbitmq.services.ConnectionProperties;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@TestMethodOrder(MethodOrderer.MethodName.class)
public class RabbitMQInOutIT extends AbstractRabbitMQIT {

    public static final String ROUTING_KEY = "rk5";
    public static final long TIMEOUT_MS = 2000;
    static final String EXCHANGE = "ex5";
    static final String EXCHANGE_NO_ACK = "ex5.noAutoAck";

    @Produce("direct:start")
    protected ProducerTemplate template;

    @Produce("direct:rabbitMQ")
    protected ProducerTemplate directProducer;

    @EndpointInject("mock:result")
    private MockEndpoint resultEndpoint;

    @Override
    protected Registry createCamelRegistry() {
        SimpleRegistry reg = new SimpleRegistry();

        Map<String, Object> args = new HashMap<>();
        args.put(RabbitMQConstants.RABBITMQ_QUEUE_TTL_KEY, 60000);
        reg.bind("args", args);

        return reg;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        ConnectionProperties connectionProperties = service.connectionProperties();
        String rabbitMQEndpoint = String
                .format("rabbitmq:%s:%d/%s?threadPoolSize=1&exchangeType=direct&username=%s&password=%s"
                        + "&autoAck=true&queue=q4&routingKey=%s&transferException=true&requestTimeout=%d&allowMessageBodySerialization=true",
                        connectionProperties.hostname(), connectionProperties.port(), EXCHANGE,
                        connectionProperties.username(), connectionProperties.password(),
                        ROUTING_KEY, TIMEOUT_MS);

        String noAutoAckEndpoint = String.format("rabbitmq:%s:%d/%s"
                                                 + "?threadPoolSize=1&exchangeType=direct&username=%s&password=%s"
                                                 + "&autoAck=false&autoDelete=false&durable=false&queue=q5&routingKey=%s"
                                                 + "&transferException=true&requestTimeout=%d&args=#args&allowMessageBodySerialization=true",
                connectionProperties.hostname(), connectionProperties.port(), EXCHANGE_NO_ACK, connectionProperties.username(),
                connectionProperties.password(), ROUTING_KEY, TIMEOUT_MS);

        return new RouteBuilder() {

            @Override
            public void configure() {

                from("direct:rabbitMQ").id("producingRoute").setHeader("routeHeader", simple("routeHeader"))
                        .to(ExchangePattern.InOut, rabbitMQEndpoint);

                from(rabbitMQEndpoint).id("consumingRoute").log("Receiving message").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        if (exchange.getIn().getBody(TestSerializableObject.class) != null) {
                            TestSerializableObject foo = exchange.getIn().getBody(TestSerializableObject.class);
                            foo.setDescription("foobar");
                        } else if (exchange.getIn().getBody(TestPartiallySerializableObject.class) != null) {
                            TestPartiallySerializableObject foo
                                    = exchange.getIn().getBody(TestPartiallySerializableObject.class);
                            foo.setNonSerializableObject(new TestNonSerializableObject());
                            foo.setDescription("foobar");
                        } else if (exchange.getIn().getBody(String.class) != null) {
                            if (exchange.getIn().getBody(String.class).contains("header")) {
                                assertEquals("String", exchange.getIn().getHeader("String"));
                                assertEquals("routeHeader", exchange.getIn().getHeader("routeHeader"));
                            }

                            if (exchange.getIn().getBody(String.class).contains("Exception")) {
                                throw new IllegalArgumentException("Boom");
                            }

                            if (exchange.getIn().getBody(String.class).contains("TimeOut")) {
                                Thread.sleep(TIMEOUT_MS * 2);
                            }

                            exchange.getIn().setBody(exchange.getIn().getBody(String.class) + " response");
                        }

                    }
                });

                from("direct:rabbitMQNoAutoAck").id("producingRouteNoAutoAck").setHeader("routeHeader", simple("routeHeader"))
                        .to(ExchangePattern.InOut, noAutoAckEndpoint);

                from(noAutoAckEndpoint).id("consumingRouteNoAutoAck").to(resultEndpoint)
                        .throwException(new IllegalStateException("test exception"));
            }
        };
    }

    @Test
    public void inOutRaceConditionTest1() {
        String reply
                = template.requestBodyAndHeader("direct:rabbitMQ", "test1", RabbitMQConstants.EXCHANGE_NAME, EXCHANGE,
                        String.class);
        assertEquals("test1 response", reply);
    }

    @Test
    public void inOutRaceConditionTest2() {
        String reply
                = template.requestBodyAndHeader("direct:rabbitMQ", "test2", RabbitMQConstants.EXCHANGE_NAME, EXCHANGE,
                        String.class);
        assertEquals("test2 response", reply);
    }

    @Test
    public void headerTest() {
        Map<String, Object> headers = new HashMap<>();

        TestSerializableObject testObject = new TestSerializableObject();
        testObject.setName("header");

        headers.put("String", "String");
        headers.put("Boolean", Boolean.valueOf(false));

        // This will blow up the connection if not removed before sending the
        // message
        headers.put("TestObject1", testObject);
        // This will blow up the connection if not removed before sending the
        // message
        headers.put("class", testObject.getClass());
        // This will mess up de-serialization if not removed before sending the
        // message
        headers.put("CamelSerialize", true);

        // populate a map and an arrayList
        Map<Object, Object> tmpMap = new HashMap<>();
        List<String> tmpList = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String name = "header" + i;
            tmpList.add(name);
            tmpMap.put(name, name);
        }
        // This will blow up the connection if not removed before sending the
        // message
        headers.put("arrayList", tmpList);
        // This will blow up the connection if not removed before sending the
        // message
        headers.put("map", tmpMap);

        String reply = template.requestBodyAndHeaders("direct:rabbitMQ", "header", headers, String.class);
        assertEquals("header response", reply);
    }

    @Test
    public void inOutExceptionTest() {
        try {
            template.requestBodyAndHeader("direct:rabbitMQ", "Exception", RabbitMQConstants.EXCHANGE_NAME, EXCHANGE,
                    String.class);
            fail("This should have thrown an exception");
        } catch (CamelExecutionException e) {
            assertEquals(IllegalArgumentException.class, e.getCause().getClass());
        } catch (Exception e) {
            fail("This should have caught CamelExecutionException");
        }
    }

    @Test
    public void inOutTimeOutTest() {
        try {
            template.requestBodyAndHeader("direct:rabbitMQ", "TimeOut", RabbitMQConstants.EXCHANGE_NAME, EXCHANGE,
                    String.class);
            fail("This should have thrown a timeOut exception");
        } catch (CamelExecutionException e) {
            // expected
        } catch (Exception e) {
            fail("This should have caught CamelExecutionException");
        }
    }

    @Test
    public void inOutNullTest() {
        template.requestBodyAndHeader("direct:rabbitMQ", null, RabbitMQConstants.EXCHANGE_NAME, EXCHANGE, Object.class);
    }

    @Test
    // should run last
    public void zRunLstMessageAckOnExceptionWhereNoAutoAckTest() throws Exception {
        Map<String, Object> headers = new HashMap<>();
        headers.put(RabbitMQConstants.EXCHANGE_NAME, EXCHANGE_NO_ACK);
        headers.put(RabbitMQConstants.ROUTING_KEY, ROUTING_KEY);

        resultEndpoint.expectedMessageCount(1);

        try {
            template.requestBodyAndHeaders("direct:rabbitMQNoAutoAck", "testMessage", headers, String.class);
            fail("This should have thrown an exception");
        } catch (CamelExecutionException e) {
            if (!(e.getCause() instanceof IllegalStateException)) {
                throw e;
            }
        }

        resultEndpoint.assertIsSatisfied();
        resultEndpoint.reset();

        resultEndpoint.expectedMessageCount(0);

        context.stop(); // On restarting the camel context, if the message was
                       // not acknowledged the message would be reprocessed

        // registry is cleaned on stop so we need to re-register
        Map<String, Object> args = new HashMap<>();
        args.put(RabbitMQConstants.RABBITMQ_QUEUE_TTL_KEY, 60000);
        context.getRegistry().bind("args", args);

        context.start();

        resultEndpoint.assertIsSatisfied();
    }

}
