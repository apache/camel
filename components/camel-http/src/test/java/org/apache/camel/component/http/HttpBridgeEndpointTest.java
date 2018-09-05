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
package org.apache.camel.component.http;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.http.handler.BasicRawQueryValidationHandler;
import org.apache.camel.component.http.handler.BasicValidationHandler;
import org.apache.camel.test.AvailablePortFinder;
import org.eclipse.jetty.server.Server;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests ported from {@link org.apache.camel.component.http4.HttpBridgeEndpointTest}.
 *
 */
public class HttpBridgeEndpointTest extends BaseHttpTest {

    private static final int PORT = AvailablePortFinder.getNextAvailable();
    private Server localServer;

    @Before
    @Override
    public void setUp() throws Exception {
        localServer = new Server(PORT);
        localServer.setHandler(handlers(
                contextHandler("/", new BasicValidationHandler("GET", null, null, getExpectedContent())),
                contextHandler("/query", new BasicRawQueryValidationHandler("GET", "x=%3B", null, getExpectedContent()))
                ));
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
    public void notBridgeEndpoint() throws Exception {
        Exchange exchange = template.request("http://host/?bridgeEndpoint=false", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_URI, "http://localhost:" + PORT + "/");
            }
        });

        assertExchange(exchange);
    }

    @Test
    public void bridgeEndpoint() throws Exception {
        Exchange exchange = template.request("http://localhost:" + PORT + "/?bridgeEndpoint=true", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_URI, "http://host:8080/");
            }
        });

        assertExchange(exchange);
    }

    @Test
    public void bridgeEndpointWithQuery() throws Exception {
        Exchange exchange = template.request("http://localhost:" + PORT + "/query?bridgeEndpoint=true", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_URI, "http://host:8080/");
                exchange.getIn().setHeader(Exchange.HTTP_QUERY, "x=%3B");
            }
        });

        assertExchange(exchange);
    }

    @Test
    public void bridgeEndpointWithRawQueryAndQuery() throws Exception {
        Exchange exchange = template.request("http://localhost:" + PORT + "/query?bridgeEndpoint=true", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_URI, "http://host:8080/");
                exchange.getIn().setHeader(Exchange.HTTP_RAW_QUERY, "x=%3B");
                exchange.getIn().setHeader(Exchange.HTTP_QUERY, "x=;");
            }
        });

        assertExchange(exchange);
    }

    @Test
    public void unsafeCharsInHttpURIHeader() throws Exception {
        Exchange exchange = template.request("http://localhost:" + PORT + "/?bridgeEndpoint=true", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_URI, "/<>{}");
            }
        });

        assertNull(exchange.getException());
        assertExchange(exchange);
    }

}
