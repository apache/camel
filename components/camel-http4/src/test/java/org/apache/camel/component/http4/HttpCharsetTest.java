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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.http4.handler.BasicValidationHandler;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @version 
 */
public class HttpCharsetTest extends BaseHttpTest {

    // default content encoding of the local test server
    private String charset = "ISO-8859-1";
    
    private HttpServer localServer;
    
    @Before
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap().
                setHttpProcessor(getBasicHttpProcessor()).
                setConnectionReuseStrategy(getConnectionReuseStrategy()).
                setResponseFactory(getHttpResponseFactory()).
                setExpectationVerifier(getHttpExpectationVerifier()).
                setSslContext(getSSLContext()).
                registerHandler("/", new BasicValidationHandler("POST", null, getBody(), getExpectedContent())).create();
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
    public void sendCharsetInExchangeProperty() throws Exception {
        Exchange exchange = template.request("http4://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort() + "/", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setProperty(Exchange.CHARSET_NAME, charset);
                exchange.getIn().setBody(getBody());
            }
        });

        assertExchange(exchange);
    }

    @Test
    public void sendByteArrayCharsetInExchangeProperty() throws Exception {
        Exchange exchange = template.request("http4://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort() + "/", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setProperty(Exchange.CHARSET_NAME, charset);
                exchange.getIn().setBody(getBody().getBytes(charset));
            }
        });

        assertExchange(exchange);
    }

    @Test
    public void sendInputStreamCharsetInExchangeProperty() throws Exception {
        Exchange exchange = template.request("http4://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort() + "/", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setProperty(Exchange.CHARSET_NAME, charset);
                exchange.getIn().setBody(new ByteArrayInputStream(getBody().getBytes(charset)));
            }
        });

        assertExchange(exchange);
    }

    protected String getBody() {
        char lattinSmallLetterAWithDiaeresis = 0x00E4;
        char lattinSmallLetterOWithDiaeresis = 0x00F6;
        char lattinSmallLetterUWithDiaeresis = 0x00FC;
        char lattinSmallLetterSharpS = 0x00DF;

        return "hl=de&q=camel+"
                + lattinSmallLetterAWithDiaeresis
                + lattinSmallLetterOWithDiaeresis
                + lattinSmallLetterUWithDiaeresis
                + lattinSmallLetterSharpS;
    }
}