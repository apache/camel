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
import org.apache.camel.Processor;
import org.apache.camel.component.http4.handler.HeaderValidationHandler;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpStatus;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.localserver.ResponseBasicUnauthorized;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @version
 */
public class HttpCompressionTest extends BaseHttpTest {

    private HttpServer localServer;
    
    @Before
    @Override
    public void setUp() throws Exception {
        Map<String, String> expectedHeaders = new HashMap<String, String>();
        expectedHeaders.put("Content-Type", "text/plain");
        expectedHeaders.put("Content-Encoding", "gzip");
        
        localServer = ServerBootstrap.bootstrap().
                setHttpProcessor(getBasicHttpProcessor()).
                setConnectionReuseStrategy(getConnectionReuseStrategy()).
                setResponseFactory(getHttpResponseFactory()).
                setExpectationVerifier(getHttpExpectationVerifier()).
                setSslContext(getSSLContext()).
                registerHandler("/", new HeaderValidationHandler("POST", null, getBody(), getExpectedContent(), expectedHeaders)).create();
        localServer.start();

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
    
    @Test
    public void compressedHttpPost() throws Exception {
        Exchange exchange = template.request("http4://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort() + "/", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "text/plain");
                exchange.getIn().setHeader(Exchange.CONTENT_ENCODING, "gzip");
                exchange.getIn().setBody(getBody());
            }
        });

        assertNotNull(exchange);

        Message out = exchange.getOut();
        assertNotNull(out);

        Map<String, Object> headers = out.getHeaders();
        assertEquals(HttpStatus.SC_OK, headers.get(Exchange.HTTP_RESPONSE_CODE));

        assertBody(out.getBody(String.class));
    }

    @Override
    protected HttpProcessor getBasicHttpProcessor() {
        List<HttpRequestInterceptor> requestInterceptors = new ArrayList<HttpRequestInterceptor>();
        requestInterceptors.add(new RequestDecompressingInterceptor());
        List<HttpResponseInterceptor> responseInterceptors = new ArrayList<HttpResponseInterceptor>();
        responseInterceptors.add(new ResponseCompressingInterceptor());
        responseInterceptors.add(new ResponseBasicUnauthorized());
        ImmutableHttpProcessor httpproc = new ImmutableHttpProcessor(requestInterceptors, responseInterceptors);
        return httpproc;
    }

    protected String getBody() {
        return "hl=en&q=camel";
    }

    static class RequestDecompressingInterceptor implements HttpRequestInterceptor {

        public void process(HttpRequest request, HttpContext context) throws HttpException, IOException {
            Header contentEncoding = request.getFirstHeader("Content-Encoding");

            if (contentEncoding != null
                    && contentEncoding.getValue().equalsIgnoreCase("gzip")) {
                HttpEntity entity = ((HttpEntityEnclosingRequest) request)
                        .getEntity();
                ((HttpEntityEnclosingRequest) request)
                        .setEntity(new GzipDecompressingEntity(entity));
            }
        }

        static class GzipDecompressingEntity extends HttpEntityWrapper {

            GzipDecompressingEntity(final HttpEntity entity) {
                super(entity);
            }

            @Override
            public InputStream getContent() throws IOException,
                    IllegalStateException {
                InputStream wrappedin = wrappedEntity.getContent();
                return new GZIPInputStream(wrappedin);
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

        public void process(HttpResponse response, HttpContext context) throws HttpException, IOException {
            response.setHeader("Content-Encoding", "gzip");
            HttpEntity entity = response.getEntity();
            response.setEntity(new GzipCompressingEntity(entity));
        }

        static class GzipCompressingEntity extends HttpEntityWrapper {

            GzipCompressingEntity(final HttpEntity entity) {
                super(entity);
            }

            @Override
            public Header getContentEncoding() {
                return new BasicHeader("Content-Encoding", "gzip");
            }

            @Override
            public void writeTo(OutputStream outstream) throws IOException {
                GZIPOutputStream gzip = new GZIPOutputStream(outstream);
                gzip.write(EntityUtils.toByteArray(wrappedEntity));
                gzip.close();
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
