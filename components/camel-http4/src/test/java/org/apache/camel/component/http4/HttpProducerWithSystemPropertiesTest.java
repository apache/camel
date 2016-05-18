/**
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
package org.apache.camel.component.http4;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.http4.handler.HeaderValidationHandler;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @version 
 */
public class HttpProducerWithSystemPropertiesTest extends BaseHttpTest {

    private static Object defaultSystemHttpAgent;
    private HttpServer localServer;

    @BeforeClass
    public static void setUpHttpAgentSystemProperty() throws Exception {
        // the 'http.agent' java system-property corresponds to the http 'User-Agent' header
        defaultSystemHttpAgent = System.setProperty("http.agent", "myCoolCamelCaseAgent");
    }

    @AfterClass
    public static void resetHttpAgentSystemProperty() throws Exception {
        if (defaultSystemHttpAgent != null) {
            System.setProperty("http.agent", String.valueOf(defaultSystemHttpAgent));
        } else {
            System.clearProperty("http.agent");
        }
    }

    @Before
    @Override
    public void setUp() throws Exception {
        Map<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put("User-Agent", "myCoolCamelCaseAgent");

        localServer = ServerBootstrap.bootstrap().
                setHttpProcessor(getBasicHttpProcessor()).
                setConnectionReuseStrategy(getConnectionReuseStrategy()).
                setResponseFactory(getHttpResponseFactory()).
                setExpectationVerifier(getHttpExpectationVerifier()).
                setSslContext(getSSLContext()).
                registerHandler("*", new HeaderValidationHandler("GET", null, null, getExpectedContent(), expectedHeaders)).create();
        localServer.start();

        super.setUp();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        if (localServer != null) {
            localServer.stop();
        }
    }

    @Test
    public void httpGetWithProxyFromSystemProperties() throws Exception {

        Exchange exchange = template.request("http4://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort() + "?useSystemProperties=true", new Processor() {
            public void process(Exchange exchange) throws Exception {
            }
        });

        assertExchange(exchange);
    }

}
