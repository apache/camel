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
package org.apache.camel.component.http;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.handler.DrinkValidationHandler;
import org.apache.camel.util.StopWatch;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.http.HttpMethods.GET;

@Disabled("Manual integration test")
public class HttpProducerLoadManualIT extends BaseHttpTest {

    private static final Logger LOG = LoggerFactory.getLogger(HttpProducerLoadManualIT.class);

    private HttpServer localServer;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap().setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/echo", new DrinkValidationHandler(GET.name(), null, null, "myHeader")).create();
        localServer.start();

        super.setUp();
    }

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        if (localServer != null) {
            localServer.stop();
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:echo")
                        .to("http://localhost:" + localServer.getLocalPort()
                            + "/echo?throwExceptionOnFailure=false");
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
