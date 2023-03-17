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

import java.io.ByteArrayInputStream;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.HttpClientConfigurer;
import org.apache.camel.spi.Registry;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled("TODO: investigate for Camel 3.0.  The test actually works fine, but the "
          + "test needs to be verified as http supports gzip by default, so some tests may "
          + "have to be changed to stay meaningful.")
public class HttpGZipEncodingTest extends BaseJettyTest {

    @Test
    public void testHttpProducerWithGzip() {
        String response = template.requestBodyAndHeader("http://localhost:" + port1 + "/gzip?httpClientConfigurer=#configurer",
                new ByteArrayInputStream("<Hello>World</Hello>".getBytes()), Exchange.CONTENT_ENCODING, "gzip", String.class);
        assertEquals("<b>Hello World</b>", response, "The response is wrong");
    }

    @Test
    public void testGzipProxy() {
        String response = template.requestBodyAndHeader("http://localhost:" + port2 + "/route?httpClientConfigurer=#configurer",
                new ByteArrayInputStream("<Hello>World</Hello>".getBytes()), Exchange.CONTENT_ENCODING, "gzip", String.class);
        assertEquals("<b>Hello World</b>", response, "The response is wrong");
    }

    @Test
    public void testGzipProducerWithGzipData() {
        String response = template.requestBodyAndHeader("direct:gzip",
                new ByteArrayInputStream("<Hello>World</Hello>".getBytes()), Exchange.CONTENT_ENCODING, "gzip",
                String.class);
        assertEquals("<b>Hello World</b>", response, "The response is wrong");
    }

    @Test
    public void testGzipGet() {
        String response = template.requestBodyAndHeader("http://localhost:" + port1 + "/gzip", null, "Accept-Encoding", "gzip",
                String.class);
        assertEquals("<b>Hello World for gzip</b>", response, "The response is wrong");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                errorHandler(noErrorHandler());

                context.getRegistry(Registry.class).bind("configurer", new HttpClientConfigurer() {
                    @Override
                    public void configureHttpClient(HttpClientBuilder clientBuilder) {
                        clientBuilder.disableContentCompression();
                    }
                });

                from("direct:gzip").marshal().gzipDeflater()
                        .setProperty(Exchange.SKIP_GZIP_ENCODING, ExpressionBuilder.constantExpression(Boolean.TRUE))
                        .to("http://localhost:" + port1 + "/gzip?httpClientConfigurer=#configurer").unmarshal().gzipDeflater();

                from("jetty:http://localhost:" + port1 + "/gzip").process(new Processor() {
                    public void process(Exchange exchange) {
                        // check the request method
                        HttpServletRequest request
                                = exchange.getIn().getHeader(Exchange.HTTP_SERVLET_REQUEST, HttpServletRequest.class);
                        if ("POST".equals(request.getMethod())) {
                            String requestBody = exchange.getIn().getBody(String.class);
                            assertEquals("<Hello>World</Hello>", requestBody, "Get a wrong request string");
                        }
                        exchange.getMessage().setHeader(Exchange.CONTENT_ENCODING, "gzip");
                        // check the Accept Encoding header
                        String header = exchange.getIn().getHeader("Accept-Encoding", String.class);
                        if (header != null && header.contains("gzip")) {
                            exchange.getMessage().setBody("<b>Hello World for gzip</b>");
                        } else {
                            exchange.getMessage().setBody("<b>Hello World</b>");
                        }
                    }
                });

                from("jetty:http://localhost:" + port2 + "/route?bridgeEndpoint=true&httpClientConfigurer=#configurer")
                        .to("http://localhost:" + port1 + "/gzip?bridgeEndpoint=true&httpClientConfigurer=#configurer");
            }
        };
    }

}
