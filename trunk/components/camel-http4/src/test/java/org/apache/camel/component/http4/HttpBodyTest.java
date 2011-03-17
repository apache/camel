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
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.http4.handler.BasicValidationHandler;
import org.apache.camel.component.http4.handler.HeaderValidationHandler;
import org.apache.http.localserver.LocalTestServer;
import org.junit.Test;


/**
 *
 * @version 
 */
public class HttpBodyTest extends BaseHttpTest {
    private String protocolString = "http4://";
    // default content encoding of the local test server
    private String charset = "ISO-8859-1";
    
    public String getProtocolString() {
        return protocolString;
    }
    
    public void setProtocolString(String protocol) {
        protocolString = protocol;
    }

    @Test
    public void httpPostWithStringBody() throws Exception {
        Exchange exchange = template.request(getProtocolString() + getHostName() + ":" + getPort() + "/", new Processor() {
            public void process(Exchange exchange) throws Exception {
                // without this property, camel use the os default encoding
                // to create the byte array for the StringRequestEntity
                exchange.setProperty(Exchange.CHARSET_NAME, charset);
                exchange.getIn().setBody(getBody());
            }
        });

        assertExchange(exchange);
    }

    @Test
    public void httpPostWithByteArrayBody() throws Exception {
        Exchange exchange = template.request(getProtocolString() + getHostName() + ":" + getPort() + "/", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(getBody().getBytes(charset));
            }
        });

        assertExchange(exchange);
    }

    @Test
    public void httpPostWithInputStreamBody() throws Exception {
        Exchange exchange = template.request(getProtocolString() + getHostName() + ":" + getPort() + "/", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(new ByteArrayInputStream(getBody().getBytes(charset)));
            }
        });

        assertExchange(exchange);
    }

    @Test
    public void httpPostWithImage() throws Exception {
        Map<String, String> expectedHeaders = new HashMap<String, String>();
        expectedHeaders.put("Content-Type", "image/jpeg");
        localServer.register("/", new HeaderValidationHandler("POST", null, null, getExpectedContent(), expectedHeaders));

        Exchange exchange = template.send(getProtocolString() + getHostName() + ":" + getPort() + "/", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(new File("src/test/data/logo.jpeg"));
                exchange.getIn().setHeader("Content-Type", "image/jpeg");
            }
        });

        assertExchange(exchange);
    }

    @Override
    protected void registerHandler(LocalTestServer server) {
        server.register("/", new BasicValidationHandler("POST", null, getBody(), getExpectedContent()));
    }

    protected String getBody() {
        return "hl=de&q=camel+rocks";
    }
}