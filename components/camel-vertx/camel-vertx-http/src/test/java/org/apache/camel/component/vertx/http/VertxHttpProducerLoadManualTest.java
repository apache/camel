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
package org.apache.camel.component.vertx.http;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Disabled("Manual test")
public class VertxHttpProducerLoadManualTest extends VertxHttpTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(VertxHttpProducerLoadManualTest.class);

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:echo")
                        .to(getProducerUri());

                from(getTestServerUri())
                        .setBody(constant("Hello World"));

            }
        };
    }

    @Test
    public void testProducerLoad() throws Exception {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < 40; i++) {
            map.put("mykey" + i, "myvalue" + i);
        }

        StopWatch watch = new StopWatch();

        // do not use template but reuse exchange/producer to be light-weight
        // and not create additional objects in the JVM as we want to analyze
        // the "raw" http producer
        Endpoint to = getMandatoryEndpoint("direct:echo");
        Producer producer = to.createProducer();
        producer.start();

        Exchange exchange = to.createExchange();
        exchange.getMessage().setHeaders(map);
        for (int i = 0; i < 10000000; i++) {
            exchange.getMessage().setBody("Message " + i);
            producer.process(exchange);
        }
        producer.stop();

        LOG.info("Took {} ms", watch.taken());
    }

}
