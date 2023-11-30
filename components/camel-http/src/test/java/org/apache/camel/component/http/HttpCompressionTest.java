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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.http.handler.HeaderValidationHandler;
import org.apache.camel.component.http.interceptor.ResponseBasicUnauthorized;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.entity.HttpEntityWrapper;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.http.common.HttpMethods.POST;
import static org.apache.hc.core5.http.ContentType.TEXT_PLAIN;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_ENCODING;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class HttpCompressionTest extends BaseHttpTest {

    private HttpServer localServer;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        Map<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put(CONTENT_TYPE, TEXT_PLAIN.getMimeType());
        expectedHeaders.put(CONTENT_ENCODING, "gzip");

        localServer = ServerBootstrap.bootstrap().setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/",
                        new HeaderValidationHandler(POST.name(), null, getBody(), getExpectedContent(), expectedHeaders))
                .create();
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
    public void compressedHttpPost() {
        Exchange exchange = template.request(
                "http://localhost:" + localServer.getLocalPort() + "/", exchange1 -> {
                    exchange1.getIn().setHeader(Exchange.CONTENT_TYPE, "text/plain");
                    exchange1.getIn().setHeader(Exchange.CONTENT_ENCODING, "gzip");
                    exchange1.getIn().setBody(getBody());
                });

        assertNotNull(exchange);

        Message out = exchange.getMessage();
        assertNotNull(out);

        Map<String, Object> headers = out.getHeaders();
        assertEquals(HttpStatus.SC_OK, headers.get(Exchange.HTTP_RESPONSE_CODE));

        assertBody(out.getBody(String.class));
    }

    @Override
    protected HttpProcessor getBasicHttpProcessor() {
        List<HttpRequestInterceptor> requestInterceptors = new ArrayList<>();
        requestInterceptors.add(new RequestDecompressingInterceptor());
        List<HttpResponseInterceptor> responseInterceptors = new ArrayList<>();
        responseInterceptors.add(new ResponseCompressingInterceptor());
        responseInterceptors.add(new ResponseBasicUnauthorized());

        return new DefaultHttpProcessor(requestInterceptors, responseInterceptors);
    }

    protected String getBody() {
        return "hl=en&q=camel";
    }

    static class RequestDecompressingInterceptor implements HttpRequestInterceptor {

        @Override
        public void process(HttpRequest request, EntityDetails details, HttpContext context) throws HttpException {
            Header contentEncoding = request.getFirstHeader(CONTENT_ENCODING);

            if (contentEncoding != null
                    && contentEncoding.getValue().equalsIgnoreCase("gzip")) {
                ClassicHttpRequest classicHttpRequest = (ClassicHttpRequest) request;
                HttpEntity entity = classicHttpRequest.getEntity();
                classicHttpRequest.setEntity(new GzipDecompressingEntity(entity));
            }
        }

        static class GzipDecompressingEntity extends HttpEntityWrapper {

            GzipDecompressingEntity(final HttpEntity entity) {
                super(entity);
            }

            @Override
            public InputStream getContent()
                    throws IOException,
                    IllegalStateException {
                return new GZIPInputStream(super.getContent());
            }

            @Override
            public long getContentLength() {
                return -1;
            }

            @Override
            public boolean isStreaming() {
                return false;
            }
        }
    }

    static class ResponseCompressingInterceptor implements HttpResponseInterceptor {

        @Override
        public void process(HttpResponse response, EntityDetails details, HttpContext context)
                throws HttpException {
            response.setHeader(CONTENT_ENCODING, "gzip");
            ClassicHttpResponse classicHttpResponse = (ClassicHttpResponse) response;
            HttpEntity entity = classicHttpResponse.getEntity();
            classicHttpResponse.setEntity(new GzipCompressingEntity(entity));
        }

        static class GzipCompressingEntity extends HttpEntityWrapper {

            GzipCompressingEntity(final HttpEntity entity) {
                super(entity);
            }

            @Override
            public String getContentEncoding() {
                return "gzip";
            }

            @Override
            public void writeTo(OutputStream outStream) throws IOException {
                try (GZIPOutputStream gzip = new GZIPOutputStream(outStream)) {
                    super.writeTo(gzip);
                }
            }

            @Override
            public long getContentLength() {
                return -1;
            }

            @Override
            public boolean isStreaming() {
                return false;
            }
        }
    }
}
