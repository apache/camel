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
                registerHandler("/content", new HttpRequestHandler() {
                    @Override
                    public void handle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
                        Header contentLengthHeader = request.getFirstHeader(Exchange.CONTENT_LENGTH);
                        String contentLength = contentLengthHeader != null ? contentLengthHeader.getValue() : "";
                        
                        //Should we expect the length in the case that we remove the header or should the header be empty?
                        assertEquals(Integer.toString(bodyContent.length()), contentLength);
                        response.setStatusCode(HttpStatus.SC_OK);
                    }
                }).create();
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
    public void testContentLengthIncorrect() throws Exception {
        Exchange out = template.request("http4://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort() + "/content", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.CONTENT_LENGTH, "1000");
                exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");
                exchange.setProperty(Exchange.SKIP_GZIP_ENCODING, Boolean.TRUE);
                exchange.getIn().setBody(new ByteArrayInputStreamCache(new ByteArrayInputStream(bodyContent.getBytes())));
            }
            
        });

        assertNotNull(out);
        assertFalse("Should not fail", out.isFailed());
        
    }
    
    
}