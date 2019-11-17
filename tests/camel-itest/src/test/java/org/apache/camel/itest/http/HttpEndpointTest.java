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

import java.io.IOException;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.http.Consts;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration
public class HttpEndpointTest extends AbstractJUnit4SpringContextTests {
    protected static HttpTestServer localServer;

    @Autowired
    protected CamelContext camelContext;

    @EndpointInject("direct:start")
    protected ProducerTemplate producer;

    @EndpointInject("mock:result")
    protected MockEndpoint mock;

    

    @BeforeClass
    public static void setUp() throws Exception {
        localServer = new HttpTestServer(null, null);
        localServer.register("/", new HttpRequestHandler() {
            public void handle(HttpRequest request, HttpResponse response,
                    HttpContext context) throws HttpException, IOException {
                response.setStatusCode(HttpStatus.SC_OK);
                response.setEntity(new StringEntity("OK", Consts.ISO_8859_1));
            }
        });
        localServer.start();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        if (localServer != null) {
            localServer.stop();
        }
    }

    @Test
    public void testMocksIsValid() throws Exception {
        mock.expectedMessageCount(1);

        producer.sendBody(null);

        mock.assertIsSatisfied();
    }
}
