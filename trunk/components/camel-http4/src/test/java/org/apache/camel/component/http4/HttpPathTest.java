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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.http4.handler.BasicValidationHandler;
import org.apache.http.localserver.LocalTestServer;
import org.junit.Test;

/**
 *
 * @version 
 */
public class HttpPathTest extends BaseHttpTest {

    @Test
    public void httpPath() throws Exception {
        Exchange exchange = template.request("http4://" + getHostName() + ":" + getPort() + "/search", new Processor() {
            public void process(Exchange exchange) throws Exception {
            }
        });

        assertExchange(exchange);
    }

    @Test
    public void httpPathHeader() throws Exception {
        Exchange exchange = template.request("http4://" + getHostName() + ":" + getPort() + "/", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_PATH, "search");
            }
        });

        assertExchange(exchange);
    }
    
    @Test
    public void httpEscapedCharacters() throws Exception {
        Exchange exchange = template.request("http4://" + getHostName() + ":" + getPort() + "/test%20/path", new Processor() {
            public void process(Exchange exchange) throws Exception {                
            }
        });

        assertExchange(exchange);
    }

    @Override
    protected void registerHandler(LocalTestServer server) {
        server.register("/search", new BasicValidationHandler("GET", null, null, getExpectedContent()));
        server.register("/test%20/path", new BasicValidationHandler("GET", null, null, getExpectedContent()));
    }
}