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
import java.nio.charset.StandardCharsets;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.HttpEndpoint;
import org.apache.camel.support.DefaultHeaderFilterStrategy;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HttpBridgeMultipartRouteTest extends BaseJettyTest {

    private static class MultipartHeaderFilterStrategy extends DefaultHeaderFilterStrategy {
        MultipartHeaderFilterStrategy() {
            initialize();
        }

        protected void initialize() {
            setLowerCase(true);
            getOutFilter().add("content-length");
            setOutFilterStartsWith(CAMEL_FILTER_STARTS_WITH);
        }
    }

    @Test
    public void testHttpClient() throws Exception {
        File jpg = new File("src/test/resources/java.jpg");
        String body = "TEST";
        HttpPost method = new HttpPost("http://localhost:" + port2 + "/test/hello");
        HttpEntity entity = MultipartEntityBuilder.create().addTextBody("body", body).addBinaryBody(jpg.getName(), jpg).build();
        method.setEntity(entity);

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(method)) {

            String responseString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            assertEquals(body, responseString);

            String numAttachments = response.getFirstHeader("numAttachments").getValue();
            assertEquals("2", numAttachments);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                errorHandler(noErrorHandler());

                Processor serviceProc = new Processor() {
                    public void process(Exchange exchange) {
                        AttachmentMessage in = exchange.getIn(AttachmentMessage.class);
                        // put the number of attachments in a response header
                        exchange.getMessage().setHeader("numAttachments", in.getAttachments().size());
                        exchange.getMessage().setBody(in.getHeader("body"));
                    }
                };

                HttpEndpoint epOut = getContext().getEndpoint(
                        "http://localhost:" + port1 + "?bridgeEndpoint=true&throwExceptionOnFailure=false", HttpEndpoint.class);
                epOut.setHeaderFilterStrategy(new MultipartHeaderFilterStrategy());

                from("jetty:http://localhost:" + port2 + "/test/hello?enableMultipartFilter=false").to(epOut);

                from("jetty://http://localhost:" + port1 + "?matchOnUriPrefix=true").process(serviceProc);
            }
        };
    }

}
