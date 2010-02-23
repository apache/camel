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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.http.handler.HeaderValidationHandler;
import org.apache.http.localserver.LocalTestServer;
import org.junit.Test;

/**
 *
 * @version $Revision$
 */
public class HttpCamelHeadersTest extends BaseHttpTest {

    @Test
    public void httpHeadersShouldPresent() throws Exception {
        Exchange exchange = template.request("http://" + getHostName() + ":" + getPort() + "/", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader("TestHeader", "test");
                exchange.getIn().setHeader("Accept-Language", "pl");
            }
        });

        assertExchange(exchange);
    }

    @Override
    protected void assertHeaders(Map<String, Object> headers) {
        super.assertHeaders(headers);

        assertEquals("test", headers.get("TestHeader"));
        assertEquals("pl", headers.get("Accept-Language"));
    }

    @Override
    protected void registerHandler(LocalTestServer server) {
        Map<String, String> expectedHeaders = new HashMap<String, String>();
        expectedHeaders.put("TestHeader", "test");
        expectedHeaders.put("Accept-Language", "pl");

        server.register("/", new HeaderValidationHandler("GET", null, null, getExpectedContent(), expectedHeaders));
    }
}