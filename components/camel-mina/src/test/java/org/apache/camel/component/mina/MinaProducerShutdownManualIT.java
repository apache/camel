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
package org.apache.camel.component.mina;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit testing for using a MinaProducer that it can shutdown properly (CAMEL-395)
 * <p>
 * Run this test from maven: mvn exec:java and see the output if there is a error.
 */
@Disabled
public class MinaProducerShutdownManualIT {

    private static final Logger LOG = LoggerFactory.getLogger(MinaProducerShutdownManualIT.class);
    private static final String URI = "mina:tcp://localhost:6321?textline=true&sync=false";
    private long start;
    private CamelContext context;

    public static void main(String[] args) throws Exception {
        MinaProducerShutdownManualIT me = new MinaProducerShutdownManualIT();
        me.testProducer();
    }

    @Test
    public void testProducer() throws Exception {
        // use shutdown hook to verify that we have stopped within 5 seconds
        Thread hook = new AssertShutdownHook();
        Runtime.getRuntime().addShutdownHook(hook);

        start = System.currentTimeMillis();

        context = new DefaultCamelContext();
        context.addRoutes(createRouteBuilder());
        context.start();

        sendMessage();

        context.stop();
    }

    private class AssertShutdownHook extends Thread {

        @Override
        public void run() {
            long diff = System.currentTimeMillis() - start;
            if (diff > 5000) {
                LOG.error("ERROR: MinaProducer should be able to shutdown within 5000 millis: time={}", diff);
            }
        }
    }

    private void sendMessage() throws Exception {
        Endpoint endpoint = context.getEndpoint(URI);
        Producer producer = endpoint.createProducer();

        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello World");

        producer.start();
        producer.process(exchange);
        producer.stop();
    }

    private RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            public void configure() {
                from(URI).to("mock:result");
            }
        };
    }
}
