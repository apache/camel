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
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.http4.handler.BasicValidationHandler;
import org.junit.Test;

/**
 *
 * @version 
 */
public class HttpMethodsTest extends BaseHttpTest {

    @Test
    public void httpGet() throws Exception {
        localServer.register("/", new BasicValidationHandler("GET", null, null, getExpectedContent()));

        Exchange exchange = template.request("http4://" + getHostName() + ":" + getPort() + "/", new Processor() {
            public void process(Exchange exchange) throws Exception {
            }
        });

        assertExchange(exchange);
    }

    @Test
    public void httpPost() throws Exception {
        localServer.register("/", new BasicValidationHandler("POST", null, null, getExpectedContent()));

        Exchange exchange = template.request("http4://" + getHostName() + ":" + getPort() + "/", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, "POST");
            }
        });

        assertExchange(exchange);
    }

    @Test
    public void httpPostWithBody() throws Exception {
        localServer.register("/", new BasicValidationHandler("POST", null, "rocks camel?", getExpectedContent()));

        Exchange exchange = template.request("http4://" + getHostName() + ":" + getPort() + "/", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("rocks camel?");
            }
        });

        assertExchange(exchange);
    }

    @Test
    public void httpPut() throws Exception {
        localServer.register("/", new BasicValidationHandler("PUT", null, null, getExpectedContent()));

        Exchange exchange = template.request("http4://" + getHostName() + ":" + getPort() + "/", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, "PUT");
            }
        });

        assertExchange(exchange);
    }

    @Test
    public void httpTrace() throws Exception {
        localServer.register("/", new BasicValidationHandler("TRACE", null, null, getExpectedContent()));

        Exchange exchange = template.request("http4://" + getHostName() + ":" + getPort() + "/", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, "TRACE");
            }
        });

        assertExchange(exchange);
    }

    @Test
    public void httpOptions() throws Exception {
        localServer.register("/", new BasicValidationHandler("OPTIONS", null, null, getExpectedContent()));

        Exchange exchange = template.request("http4://" + getHostName() + ":" + getPort() + "/", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, "OPTIONS");
            }
        });

        assertExchange(exchange);
    }

    @Test
    public void httpDelete() throws Exception {
        localServer.register("/", new BasicValidationHandler("DELETE", null, null, getExpectedContent()));

        Exchange exchange = template.request("http4://" + getHostName() + ":" + getPort() + "/", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, "DELETE");
            }
        });

        assertExchange(exchange);
    }

    @Test
    public void httpHead() throws Exception {
        localServer.register("/", new BasicValidationHandler("HEAD", null, null, getExpectedContent()));

        Exchange exchange = template.request("http4://" + getHostName() + ":" + getPort() + "/", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, "HEAD");
            }
        });

        assertNotNull(exchange);

        Message out = exchange.getOut();
        assertNotNull(out);
        assertHeaders(out.getHeaders());
        assertNull(out.getBody(String.class));
    }
}