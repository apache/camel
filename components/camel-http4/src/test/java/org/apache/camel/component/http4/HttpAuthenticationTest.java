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
import org.apache.camel.component.http4.handler.AuthenticationValidationHandler;
import org.apache.http.HttpStatus;
import org.apache.http.localserver.LocalTestServer;
import org.apache.http.localserver.RequestBasicAuth;
import org.apache.http.localserver.ResponseBasicUnauthorized;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.ResponseContent;
import org.junit.Test;

/**
 *
 * @version 
 */
public class HttpAuthenticationTest extends BaseHttpTest {

    private String user = "camel";
    private String password = "password";

    @Test
    public void basicAuthenticationShouldSuccess() throws Exception {
        Exchange exchange = template.request("http4://" + getHostName() + ":" + getPort() + "/search?authUsername=" + user + "&authPassword=" + password, new Processor() {
            public void process(Exchange exchange) throws Exception {
            }
        });

        assertExchange(exchange);
    }
    
    
    @Test
    public void basicAuthenticationPreemptiveShouldSuccess() throws Exception {
        Exchange exchange = template.request("http4://" + getHostName() + ":" + getPort() + "/search?authUsername=" + user + "&authPassword=" 
            + password + "&authenticationPreemptive=true", new Processor() {
                public void process(Exchange exchange) throws Exception {
                }
            });

        assertExchange(exchange);
    }

    @Test
    public void basicAuthenticationShouldFailWithoutCreds() throws Exception {
        Exchange exchange = template.request("http4://" + getHostName() + ":" + getPort() + "/search?throwExceptionOnFailure=false", new Processor() {
            public void process(Exchange exchange) throws Exception {
            }
        });

        assertExchangeFailed(exchange);
    }

    @Test
    public void basicAuthenticationShouldFailWithWrongCreds() throws Exception {
        Exchange exchange = template.request("http4://" + getHostName() + ":" + getPort() + "/search?throwExceptionOnFailure=false&authUsername=camel&authPassword=wrong", new Processor() {
            public void process(Exchange exchange) throws Exception {
            }
        });

        assertExchangeFailed(exchange);
    }

    @Override
    protected BasicHttpProcessor getBasicHttpProcessor() {
        BasicHttpProcessor httpproc = new BasicHttpProcessor();
        httpproc.addInterceptor(new RequestBasicAuth());

        httpproc.addInterceptor(new ResponseContent());
        httpproc.addInterceptor(new ResponseBasicUnauthorized());

        return httpproc;
    }

    @Override
    protected void registerHandler(LocalTestServer server) {
        server.register("/search", new AuthenticationValidationHandler("GET", null, null, getExpectedContent(), user, password));
    }

    protected void assertExchangeFailed(Exchange exchange) {
        assertNotNull(exchange);

        Message out = exchange.getOut();
        assertNotNull(out);

        Map<String, Object> headers = out.getHeaders();
        assertEquals(HttpStatus.SC_UNAUTHORIZED, headers.get(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("0", headers.get("Content-Length"));
        assertNull(headers.get("Content-Type"));

        assertEquals("", out.getBody(String.class));
    }
}