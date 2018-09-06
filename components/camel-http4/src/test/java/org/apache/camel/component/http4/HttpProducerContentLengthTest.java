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

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.converter.stream.ByteArrayInputStreamCache;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HttpProducerContentLengthTest extends BaseHttpTest {
    
    private HttpServer localServer;
    
    private final String bodyContent = "{ \n \"content\"=\"This is content\" \n }";
    
    
    @Before
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap().
                setHttpProcessor(getBasicHttpProcessor()).
                setConnectionReuseStrategy(getConnectionReuseStrategy()).
                setResponseFactory(getHttpResponseFactory()).
                setExpectationVerifier(getHttpExpectationVerifier()).
                setSslContext(getSSLContext()).
                registerHandler("/content-streamed", new HttpRequestHandler() {
                    @Override
                    public void handle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
                        Header contentLengthHeader = request.getFirstHeader(Exchange.CONTENT_LENGTH);
                        String contentLength = contentLengthHeader != null ? contentLengthHeader.getValue() : "";
                        Header transferEncodingHeader = request.getFirstHeader(Exchange.TRANSFER_ENCODING);
                        String transferEncoding = transferEncodingHeader != null ? transferEncodingHeader.getValue() : "";
                        
                        //Request Body Chunked if no Content-Length set.
                        assertEquals("", contentLength);
                        assertEquals("chunked", transferEncoding);
                        response.setStatusCode(HttpStatus.SC_OK);
                    }
                }).registerHandler("/content-not-streamed", new HttpRequestHandler() {
                    @Override
                    public void handle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
                        Header contentLengthHeader = request.getFirstHeader(Exchange.CONTENT_LENGTH);
                        String contentLength = contentLengthHeader != null ? contentLengthHeader.getValue() : "";
                        Header transferEncodingHeader = request.getFirstHeader(Exchange.TRANSFER_ENCODING);
                        String transferEncoding = transferEncodingHeader != null ? transferEncodingHeader.getValue() : "";
                        
                        //Content-Length should match byte array
                        assertEquals("35", contentLength);
                        assertEquals("", transferEncoding);
                        response.setStatusCode(HttpStatus.SC_OK);
                    }
                })
                .create();
            
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
    public void testContentLengthStream() throws Exception {
        Exchange out = template.request("http4://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort() + "/content-streamed?bridgeEndpoint=true", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.CONTENT_LENGTH, "1000");
                exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");
                exchange.getIn().setBody(new ByteArrayInputStreamCache(new ByteArrayInputStream(bodyContent.getBytes())));
            }
            
        });

        assertNotNull(out);
        assertFalse("Should not fail", out.isFailed());
        
    }
    
    @Test
    public void testContentLengthNotStreamed() throws Exception {
        Exchange out = template.request("http4://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort() + "/content-not-streamed?bridgeEndpoint=true", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.CONTENT_LENGTH, "1000");
                exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");
                exchange.getIn().setBody(bodyContent.getBytes());
            }
            
        });

        assertNotNull(out);
        assertFalse("Should not fail", out.isFailed());
        
    }
    
    
}