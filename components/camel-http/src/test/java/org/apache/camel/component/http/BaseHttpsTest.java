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

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.hc.core5.http.HttpStatus;

import static org.apache.hc.core5.http.HttpHeaders.CONTENT_LENGTH;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public abstract class BaseHttpsTest extends HttpsServerTestSupport {

    protected void assertExchange(Exchange exchange) {
        assertNotNull(exchange);
        assertNull(exchange.getException());
        Message out = exchange.getMessage();
        assertNotNull(out);
        assertHeaders(out.getHeaders());
        assertBody(out.getBody(String.class));
    }

    protected void assertHeaders(Map<String, Object> headers) {
        assertEquals(HttpStatus.SC_OK, headers.get(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("12", headers.get(CONTENT_LENGTH));
        assertNotNull(headers.get(CONTENT_TYPE), "Should have Content-Type header");
    }

    protected void assertBody(String body) {
        assertEquals(getExpectedContent(), body);
    }

    protected String getExpectedContent() {
        return "camel rocks!";
    }
}
