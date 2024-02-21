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
package org.apache.camel.itest.http;

import java.nio.charset.StandardCharsets;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@CamelSpringTest
@ContextConfiguration
public class HttpMaxConnectionPerHostTest {
    protected static HttpTestServer localServer;

    @Autowired
    protected CamelContext camelContext;

    @EndpointInject("direct:start")
    protected ProducerTemplate producer;

    @EndpointInject("mock:result")
    protected MockEndpoint mock;

    @BeforeAll
    public static void setUp() throws Exception {
        localServer = new HttpTestServer();
        localServer.register("/", (request, response, context) -> {
            response.setCode(HttpStatus.SC_OK);
            response.setEntity(new StringEntity("OK", StandardCharsets.ISO_8859_1));
        });
        localServer.start();
    }

    @AfterAll
    public static void tearDown() throws Exception {
        if (localServer != null) {
            localServer.stop();
        }
    }

    @Test
    void testMocksIsValid() throws Exception {
        mock.expectedMessageCount(1);

        producer.sendBody(null);

        mock.assertIsSatisfied();
    }
}
