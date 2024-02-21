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
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.component.http.handler.BasicValidationHandler;
import org.apache.camel.component.http.handler.HeaderValidationHandler;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.ContentType.IMAGE_JPEG;
import static org.apache.camel.component.http.HttpMethods.POST;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE;

public class HttpBodyTest extends BaseHttpTest {
    private String protocolString = "http://";
    // default content encoding of the local test server
    private final String charset = "ISO-8859-1";
    private HttpServer localServer;
    private String endpointUrl;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        Map<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put(CONTENT_TYPE, IMAGE_JPEG.getMimeType());

        localServer = ServerBootstrap.bootstrap().setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/post", new BasicValidationHandler(POST.name(), null, getBody(), getExpectedContent()))
                .register("/post1",
                        new HeaderValidationHandler(POST.name(), null, null, getExpectedContent(), expectedHeaders))
                .create();
        localServer.start();

        endpointUrl = getProtocolString() + "localhost:" + localServer.getLocalPort();

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

    public String getProtocolString() {
        return protocolString;
    }

    public void setProtocolString(String protocol) {
        protocolString = protocol;
    }

    @Test
    public void httpPostWithStringBody() {
        Exchange exchange = template.request(endpointUrl + "/post", exchange1 -> {
            // without this property, camel use the os default encoding
            // to create the byte array for the StringRequestEntity
            exchange1.setProperty(Exchange.CHARSET_NAME, charset);
            exchange1.getIn().setBody(getBody());
        });

        assertExchange(exchange);
    }

    @Test
    public void httpPostWithByteArrayBody() {
        Exchange exchange
                = template.request(endpointUrl + "/post", exchange1 -> exchange1.getIn().setBody(getBody().getBytes(charset)));

        assertExchange(exchange);
    }

    @Test
    public void httpPostWithInputStreamBody() {
        Exchange exchange = template.request(endpointUrl + "/post",
                exchange1 -> exchange1.getIn().setBody(new ByteArrayInputStream(getBody().getBytes(charset))));

        assertExchange(exchange);
    }

    @Test
    public void httpPostWithImage() {

        Exchange exchange = template.send(endpointUrl + "/post1", exchange1 -> {
            exchange1.getIn().setBody(new File("src/test/data/logo.jpeg"));
            exchange1.getIn().setHeader(CONTENT_TYPE, IMAGE_JPEG.getMimeType());
        });

        assertExchange(exchange);
    }

    protected String getBody() {
        return "hl=de&q=camel+rocks";
    }
}
