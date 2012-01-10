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

import java.io.IOException;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.http4.handler.BasicValidationHandler;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.junit.Test;

/**
 *
 */
public class HttpRedirectTest extends BaseHttpTest {

    @Test
    public void httpRedirect() throws Exception {
        // force a 301 redirect
        localServer.register("/test", new RedirectHandler(HttpStatus.SC_MOVED_PERMANENTLY));

        String uri = "http4://" + getHostName() + ":" + getPort()
                + "/test?httpClient.handleRedirects=false&httpClient.soTimeout=60000&httpClient.connectionTimeout=60000"
                + "&httpClient.staleCheckingEnabled=false";
        Exchange out = template.request(uri, new Processor() {
            public void process(Exchange exchange) throws Exception {
                // no data
            }
        });

        assertNotNull(out);
        HttpOperationFailedException cause = out.getException(HttpOperationFailedException.class);
        assertNotNull(cause);
        assertEquals(HttpStatus.SC_MOVED_PERMANENTLY, cause.getStatusCode());
        assertEquals("http4://" + getHostName() + ":" + getPort() + "/someplaceelse", cause.getRedirectLocation());
    }

    @Test
    public void httpHandleRedirect() throws Exception {
        // force a 301 redirect
        localServer.register("/test", new RedirectHandler(HttpStatus.SC_MOVED_PERMANENTLY));
        localServer.register("/someplaceelse", new BasicValidationHandler("GET", null, null, "Bye World"));

        String uri = "http4://" + getHostName() + ":" + getPort()
                + "/test?httpClient.soTimeout=60000&httpClient.connectionTimeout=60000"
                + "&httpClient.staleCheckingEnabled=false";
        Exchange out = template.request(uri, new Processor() {
            public void process(Exchange exchange) throws Exception {
                // no data
            }
        });

        assertNotNull(out);
        assertEquals(HttpStatus.SC_OK, out.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("Bye World", out.getOut().getBody(String.class));
    }

    private final class RedirectHandler implements HttpRequestHandler {

        private final int code;

        private RedirectHandler(int code) {
            this.code = code;
        }

        public void handle(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) throws HttpException, IOException {
            httpResponse.setHeader("location", "http4://" + getHostName() + ":" + getPort() + "/someplaceelse");
            httpResponse.setStatusCode(code);
        }
    }

}
