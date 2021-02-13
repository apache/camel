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

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.handler.DrinkValidationHandler;
import org.apache.camel.util.StopWatch;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.http.HttpMethods.GET;

@Disabled("Manual test")
public class HttpProducerLoadTest extends BaseHttpTest {

    private static final Logger LOG = LoggerFactory.getLogger(HttpProducerLoadTest.class);

    private HttpServer localServer;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap().setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setExpectationVerifier(getHttpExpectationVerifier()).setSslContext(getSSLContext())
                .registerHandler("/echo", new DrinkValidationHandler(GET.name(), null, null, "myHeader")).create();
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
        StopWatch watch = new StopWatch();
        for (int i = 0; i < 10000000; i++) {
            fluentTemplate.to("direct:echo")
                    .withHeader("a", "aaa")
                    .withHeader("b", "bbb")
                    .withHeader("c", "ccc")
                    .withHeader("d", "ddd")
                    .withHeader("e", "eee")
                    .withHeader("f", "fff")
                    .withHeader("g", "ggg")
                    .withHeader("h", "hhh")
                    .withHeader("i", "iii")
                    .withHeader("j", "jjj")
                    .withHeader("a2", "aaa")
                    .withHeader("b2", "bbb")
                    .withHeader("c2", "ccc")
                    .withHeader("d2", "ddd")
                    .withHeader("e2", "eee")
                    .withHeader("f2", "fff")
                    .withHeader("g2", "ggg")
                    .withHeader("h2", "hhh")
                    .withHeader("i2", "iii")
                    .withHeader("j2", "jjj")
                    .withHeader("a3", "aaa")
                    .withHeader("b3", "bbb")
                    .withHeader("c3", "ccc")
                    .withHeader("d3", "ddd")
                    .withHeader("e3", "eee")
                    .withHeader("f3", "fff")
                    .withHeader("g3", "ggg")
                    .withHeader("h3", "hhh")
                    .withHeader("i3", "iii")
                    .withHeader("j3", "jjj")
                    .withHeader("a4", "aaa")
                    .withHeader("b4", "bbb")
                    .withHeader("c4", "ccc")
                    .withHeader("d4", "ddd")
                    .withHeader("e4", "eee")
                    .withHeader("f4", "fff")
                    .withHeader("g4", "ggg")
                    .withHeader("h4", "hhh")
                    .withHeader("i4", "iii")
                    .withHeader("j4", "jjj")
                    .withHeader("myHeader", "msg" + i).send();
        }
        LOG.info("Took {} ms", watch.taken());
    }

}
