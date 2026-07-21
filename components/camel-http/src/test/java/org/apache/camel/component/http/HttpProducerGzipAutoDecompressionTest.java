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
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.apache.camel.Exchange;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.entity.HttpEntityWrapper;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.RequestValidateHost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.apache.hc.core5.http.HttpHeaders.CONTENT_ENCODING;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for CAMEL-24234: HttpClient 5.6+ auto-decompresses response bodies but may leave stale gzip Content-Encoding
 * headers (and entity metadata), which must not trigger a second decompression in {@link HttpProducer}.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HttpProducerGzipAutoDecompressionTest extends HttpServerTestSupport {

    private static final String RESPONSE_BODY = "camel rocks!";

    private HttpServer localServer;

    @Override
    public void setupResources() throws Exception {
        localServer = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost")
                .setHttpProcessor(getBasicHttpProcessor())
                .register("/", (request, response, context) -> {
                    response.setCode(HttpStatus.SC_OK);
                    response.setEntity(new StringEntity(RESPONSE_BODY));
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

    @Override
    protected HttpProcessor getBasicHttpProcessor() {
        List<HttpRequestInterceptor> requestInterceptors = new ArrayList<>();
        requestInterceptors.add(new RequestValidateHost());
        List<HttpResponseInterceptor> responseInterceptors = new ArrayList<>();
        responseInterceptors.add(new ResponseCompressingInterceptor());
        return new DefaultHttpProcessor(requestInterceptors, responseInterceptors);
    }

    @BeforeEach
    void resetContentCompressionDisabled() {
        HttpComponent http = context.getComponent("http", HttpComponent.class);
        http.setContentCompressionDisabled(false);
    }

    @Test
    @Order(1)
    void shouldDecompressGzipBodyWhenAutoDecompressionDisabled() {
        HttpComponent http = context.getComponent("http", HttpComponent.class);
        http.setContentCompressionDisabled(true);
        try {
            Exchange exchange = template.request("http://localhost:" + localServer.getLocalPort() + "/",
                    exchange1 -> {
                    });

            assertThat(exchange.getException()).isNull();
            assertThat(exchange.getMessage().getBody(String.class)).isEqualTo(RESPONSE_BODY);
        } finally {
            http.setContentCompressionDisabled(false);
        }
    }

    @Test
    @Order(2)
    void shouldReadPlainBodyWhenHttpClientAlreadyDecompressedWithStaleGzipHeaders() {
        Exchange exchange = template.request("http://localhost:" + localServer.getLocalPort() + "/",
                exchange1 -> {
                });

        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class))
                .isEqualTo(HttpStatus.SC_OK);
        assertThat(exchange.getMessage().getBody(String.class)).isEqualTo(RESPONSE_BODY);
        assertThat(exchange.getMessage().getHeader(Exchange.CONTENT_ENCODING, String.class)).isNull();
    }

    static final class ResponseCompressingInterceptor implements HttpResponseInterceptor {

        @Override
        public void process(HttpResponse response, EntityDetails details, HttpContext context) throws HttpException {
            response.setHeader(CONTENT_ENCODING, "gzip");
            ClassicHttpResponse classicHttpResponse = (ClassicHttpResponse) response;
            HttpEntity entity = classicHttpResponse.getEntity();
            classicHttpResponse.setEntity(new GzipCompressingEntity(entity));
        }

        static final class GzipCompressingEntity extends HttpEntityWrapper {

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
