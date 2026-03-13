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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit testing for using a MinaProducer that it can shutdown properly (CAMEL-395)
 */
public class MinaProducerShutdownTest extends BaseMinaTest {

    @Test
    public void testProducer() throws Exception {
        Endpoint endpoint = context.getEndpoint(getUri());
        Producer producer = endpoint.createProducer();

        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello World");

        StopWatch stopWatch = new StopWatch();
        producer.start();
        producer.process(exchange);
        producer.stop();

        context.stop();
        long diff = stopWatch.taken();

        assertTrue(diff < 5000, "MinaProducer should be able to shutdown within 5000 millis: time=" + diff);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            public void configure() {
                from(getUri()).to("mock:result");
            }
        };
    }

    private String getUri() {
        return "mina:tcp://localhost:" + getPort() + "?textline=true&sync=false";
    }
}
