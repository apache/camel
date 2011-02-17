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

import java.net.ConnectException;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.handler.BasicValidationHandler;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.localserver.LocalTestServer;
import org.junit.Test;

/**
 * @version 
 */
public class HttpNoConnectionRedeliveryTest extends BaseHttpTest {

    @Test
    public void httpConnectionOk() throws Exception {
        Exchange exchange = template.request("direct:start", null);

        assertExchange(exchange);
    }

    @Test
    public void httpConnectionNotOk() throws Exception {
        // stop server so there are no connection
        localServer.stop();

        Exchange exchange = template.request("direct:start", null);
        assertTrue(exchange.isFailed());

        HttpHostConnectException cause = assertIsInstanceOf(HttpHostConnectException.class, exchange.getException());
        assertIsInstanceOf(ConnectException.class, cause.getCause());

        assertEquals(true, exchange.getIn().getHeader(Exchange.REDELIVERED));
        assertEquals(4, exchange.getIn().getHeader(Exchange.REDELIVERY_COUNTER));
    }

    @Override
    protected void registerHandler(LocalTestServer server) {
        server.register("/search", new BasicValidationHandler("GET", null, null, getExpectedContent()));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .onException(ConnectException.class)
                        .maximumRedeliveries(4)
                        .backOffMultiplier(2)
                        .redeliveryDelay(100)
                        .maximumRedeliveryDelay(5000)
                        .useExponentialBackOff()
                    .end()
                    .to("http4://" + getHostName() + ":" + getPort() + "/search");
            }
        };
    }
}