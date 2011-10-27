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

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.http4.handler.BasicValidationHandler;
import org.apache.http.HttpStatus;
import org.apache.http.localserver.LocalTestServer;
import org.junit.Test;

/**
 *
 * @version 
 */
public class HttpThrowExceptionOnFailureTest extends BaseHttpTest {

    @Test
    public void httpGetWhichReturnsHttp501() throws Exception {
        Exchange exchange = template.request("http4://" + getHostName() + ":" + getPort() + "/XXX?throwExceptionOnFailure=false", new Processor() {
            public void process(Exchange exchange) throws Exception {
            }
        });

        assertNotNull(exchange);

        Message out = exchange.getOut();
        assertNotNull(out);

        Map<String, Object> headers = out.getHeaders();
        assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, headers.get(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("0", headers.get("Content-Length"));
    }

    @Test
    public void httpGetWhichReturnsHttp501ShouldThrowAnException() throws Exception {
        Exchange reply = template.request("http4://" + getHostName() + ":" + getPort() + "/XXX?throwExceptionOnFailure=true", new Processor() {
            public void process(Exchange exchange) throws Exception {
            }
        });

        Exception e = reply.getException();
        assertNotNull("Should have thrown an exception", e);
        HttpOperationFailedException cause = assertIsInstanceOf(HttpOperationFailedException.class, e);
        assertEquals(501, cause.getStatusCode());
    }

    @Override
    protected void registerHandler(LocalTestServer server) {
        server.register("/", new BasicValidationHandler("GET", null, null, getExpectedContent()));
    }
}