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
import org.apache.camel.Processor;
import org.apache.camel.component.http4.handler.BasicValidationHandler;
import org.apache.http.localserver.LocalTestServer;
import org.junit.Test;

/**
 * @version
 */
public class HttpNoConnectionTest extends BaseHttpTest {

    @Test
    public void httpConnectionOk() throws Exception {
        Exchange exchange = template.request("http4://" + getHostName() + ":" + getPort() + "/search", new Processor() {
            public void process(Exchange exchange) throws Exception {
            }
        });

        assertExchange(exchange);
    }

    @Test
    public void httpConnectionNotOk() throws Exception {
        String url = "http4://" + getHostName() + ":" + getPort() + "/search";
        // stop server so there are no connection
        localServer.stop();
        localServer.awaitTermination(1000);

        Exchange reply = template.request(url, null);
        Exception e = reply.getException();
        assertNotNull("Should have thrown an exception", e);
        ConnectException cause = assertIsInstanceOf(ConnectException.class, e);
        assertTrue(cause.getMessage().contains("refused"));
    }

    @Override
    protected void registerHandler(LocalTestServer server) {
        server.register("/search", new BasicValidationHandler("GET", null, null, getExpectedContent()));
    }

}
