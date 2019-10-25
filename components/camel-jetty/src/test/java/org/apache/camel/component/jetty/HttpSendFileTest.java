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
package org.apache.camel.component.jetty;

import java.io.File;
import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class HttpSendFileTest extends BaseJettyTest {

    @Test
    public void testSendImage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.message(0).body().isInstanceOf(InputStream.class);
        mock.message(0).header("Content-Type").isEqualTo("image/jpeg");

        Exchange out = template.send("http://localhost:{{port}}/myapp/myservice", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(new File("src/test/data/logo.jpeg"));
                exchange.getIn().setHeader("Content-Type", "image/jpeg");
            }
        });

        assertMockEndpointsSatisfied();

        assertEquals("OK", out.getOut().getBody(String.class));
        assertEquals("text/plain", out.getOut().getHeader("Content-Type"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("jetty:http://localhost:{{port}}/myapp/myservice").to("mock:result").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String body = exchange.getIn().getBody(String.class);
                        assertNotNull("Body should not be null", body);
                    }
                }).transform(constant("OK")).setHeader("Content-Type", constant("text/plain"));
            }
        };
    }

}
