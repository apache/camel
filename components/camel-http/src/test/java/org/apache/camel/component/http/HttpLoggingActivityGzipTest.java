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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class HttpLoggingActivityGzipTest extends HttpCompressionTest {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        HttpComponent http = context.getComponent("http", HttpComponent.class);
        http.setLogHttpActivity(true);
        return context;
    }

    @Test
    public void compressedHttpPostWithLogging() {
        Exchange exchange = template.request(
                "http://localhost:" + localServer.getLocalPort() + "/", exchange1 -> {
                    exchange1.getIn().setHeader(Exchange.CONTENT_TYPE, "text/plain");
                    exchange1.getIn().setHeader(Exchange.CONTENT_ENCODING, "gzip");
                    exchange1.getIn().setBody(getBody());
                });

        assertNotNull(exchange);
        assertNull(exchange.getException());

        Message out = exchange.getMessage();
        assertNotNull(out);
        assertEquals(HttpStatus.SC_OK, out.getHeaders().get(Exchange.HTTP_RESPONSE_CODE));
        assertBody(out.getBody(String.class));
    }
}
