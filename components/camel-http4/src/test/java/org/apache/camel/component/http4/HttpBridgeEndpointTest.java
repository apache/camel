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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.http4.handler.BasicValidationHandler;
import org.apache.http.localserver.LocalTestServer;
import org.junit.Test;

/**
 *
 * @version 
 */
public class HttpBridgeEndpointTest extends BaseHttpTest {

    @Test
    public void notBridgeEndpoint() throws Exception {
        Exchange exchange = template.request("http4://host/?bridgeEndpoint=false", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_URI, "http://" + getHostName() + ":" + getPort() + "/");
            }
        });

        assertExchange(exchange);
    }

    @Test
    public void bridgeEndpoint() throws Exception {
        Exchange exchange = template.request("http4://" + getHostName() + ":" + getPort() + "/?bridgeEndpoint=true", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_URI, "http://host:8080/");
            }
        });

        assertExchange(exchange);
    }

    @Override
    protected void registerHandler(LocalTestServer server) {
        server.register("/", new BasicValidationHandler("GET", null, null, getExpectedContent()));
    }
}