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

import java.io.ByteArrayInputStream;

import org.apache.camel.Exchange;
import org.apache.camel.component.http.handler.BasicValidationHandler;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.http.HttpMethods.POST;

public class HttpCharsetTest extends BaseHttpTest {

    // default content encoding of the local test server
    private final String charset = "ISO-8859-1";

    private HttpServer localServer;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap().setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/", new BasicValidationHandler(POST.name(), null, getBody(), getExpectedContent())).create();
        localServer.start();

        super.setUp();
    }

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        if (localServer != null) {
            localServer.stop();
        }
    }

    @Test
    public void sendCharsetInExchangeProperty() {
        Exchange exchange = template.request(
                "http://localhost:" + localServer.getLocalPort() + "/", exchange1 -> {
                    exchange1.setProperty(Exchange.CHARSET_NAME, charset);
                    exchange1.getIn().setBody(getBody());
                });

        assertExchange(exchange);
    }

    @Test
    public void sendByteArrayCharsetInExchangeProperty() {
        Exchange exchange = template.request(
                "http://localhost:" + localServer.getLocalPort() + "/", exchange1 -> {
                    exchange1.setProperty(Exchange.CHARSET_NAME, charset);
                    exchange1.getIn().setBody(getBody().getBytes(charset));
                });

        assertExchange(exchange);
    }

    @Test
    public void sendInputStreamCharsetInExchangeProperty() {
        Exchange exchange = template.request(
                "http://localhost:" + localServer.getLocalPort() + "/", exchange1 -> {
                    exchange1.setProperty(Exchange.CHARSET_NAME, charset);
                    exchange1.getIn().setBody(new ByteArrayInputStream(getBody().getBytes(charset)));
                });

        assertExchange(exchange);
    }

    protected String getBody() {
        char latinSmallLetterAWithDiaeresis = 0x00E4;
        char latinSmallLetterOWithDiaeresis = 0x00F6;
        char latinSmallLetterUWithDiaeresis = 0x00FC;
        char latinSmallLetterSharpS = 0x00DF;

        return "hl=de&q=camel+"
               + latinSmallLetterAWithDiaeresis
               + latinSmallLetterOWithDiaeresis
               + latinSmallLetterUWithDiaeresis
               + latinSmallLetterSharpS;
    }
}
