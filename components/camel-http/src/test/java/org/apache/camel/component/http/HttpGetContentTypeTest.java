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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.camel.Exchange;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Test;

public class HttpGetContentTypeTest extends BaseHttpTest {

    private HttpServer localServer;

    @Override
    public void setupResources() throws Exception {
        localServer = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost")
                .setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy())
                .setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/myget", (request, response, context) -> {
                    Header ctHeader = request.getFirstHeader(Exchange.CONTENT_TYPE);
                    String ct = ctHeader != null ? ctHeader.getValue() : "";
                    assertEquals("application/json", ct);
                    response.setEntity(new StringEntity(getExpectedContent()));
                    response.setCode(HttpStatus.SC_OK);
                })
                .create();
        localServer.start();
    }

    @Override
    public void cleanupResources() throws Exception {
        if (localServer != null) {
            localServer.stop();
        }
    }

    @Test
    public void sendGetContentType() {
        Exchange exchange = template.request(
                "http://localhost:" + localServer.getLocalPort() + "/myget?getWithBody=true", exchange1 -> {
                    exchange1.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");
                    exchange1.getIn().setBody("");
                });

        assertExchange(exchange);
    }

    @Override
    protected String expectedContentLength() {
        return "19";
    }

    @Override
    protected String getExpectedContent() {
        return "CT=application/json";
    }
}
