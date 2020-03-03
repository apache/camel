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
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.apache.camel.component.http.HttpMethods.POST;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.apache.http.entity.ContentType.IMAGE_JPEG;

public class HttpBodyTest extends BaseHttpTest {
    private String protocolString = "http://";
    // default content encoding of the local test server
    private String charset = "ISO-8859-1";
    private HttpServer localServer;
    private String endpointUrl;

    @Before
    @Override
    public void setUp() throws Exception {
        Map<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put(CONTENT_TYPE, IMAGE_JPEG.getMimeType());

        localServer = ServerBootstrap.bootstrap().
                setHttpProcessor(getBasicHttpProcessor()).
                setConnectionReuseStrategy(getConnectionReuseStrategy()).
                setResponseFactory(getHttpResponseFactory()).
                setExpectationVerifier(getHttpExpectationVerifier()).
                setSslContext(getSSLContext()).
                registerHandler("/post", new BasicValidationHandler(POST.name(), null, getBody(), getExpectedContent())).
                registerHandler("/post1", new HeaderValidationHandler(POST.name(), null, null, getExpectedContent(), expectedHeaders)).
                create();
        localServer.start();

        endpointUrl = getProtocolString() + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort();

        super.setUp();
    }

    @After
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
    public void httpPostWithStringBody() throws Exception {
        Exchange exchange = template.request(endpointUrl + "/post", exchange1 -> {
            // without this property, camel use the os default encoding
            // to create the byte array for the StringRequestEntity
            exchange1.setProperty(Exchange.CHARSET_NAME, charset);
            exchange1.getIn().setBody(getBody());
        });

        assertExchange(exchange);
    }

    @Test
    public void httpPostWithByteArrayBody() throws Exception {
        Exchange exchange = template.request(endpointUrl + "/post", exchange1 -> exchange1.getIn().setBody(getBody().getBytes(charset)));

        assertExchange(exchange);
    }

    @Test
    public void httpPostWithInputStreamBody() throws Exception {
        Exchange exchange = template.request(endpointUrl + "/post", exchange1 -> exchange1.getIn().setBody(new ByteArrayInputStream(getBody().getBytes(charset))));

        assertExchange(exchange);
    }

    @Test
    public void httpPostWithImage() throws Exception {

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
