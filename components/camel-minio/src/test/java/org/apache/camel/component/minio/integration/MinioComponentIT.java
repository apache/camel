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
package org.apache.camel.component.minio.integration;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.minio.MinioConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinioComponentIT extends MinioIntegrationTestSupport {

    @EndpointInject("direct:start")
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    void sendInOnly() throws Exception {
        result.expectedMessageCount(2);

        Exchange exchange1 = template.send("direct:start", ExchangePattern.InOnly, exchange -> {
            exchange.getIn().setHeader(MinioConstants.OBJECT_NAME, "CamelUnitTest1");
            exchange.getIn().setBody("This is my bucket content.");
        });

        Exchange exchange2 = template.send("direct:start", ExchangePattern.InOnly, exchange -> {
            exchange.getIn().setHeader(MinioConstants.OBJECT_NAME, "CamelUnitTest2");
            exchange.getIn().setBody("This is my bucket content.");
        });

        MockEndpoint.assertIsSatisfied(context);

        assertResultExchange(result.getExchanges().get(0));
        assertResultExchange(result.getExchanges().get(1));

        assertResponseMessage(exchange1.getIn());
        assertResponseMessage(exchange2.getIn());
    }

    @Test
    void sendInOut() throws Exception {
        result.expectedMessageCount(1);

        Exchange exchange = template.send("direct:start", ExchangePattern.InOut, exchange1 -> {
            exchange1.getIn().setHeader(MinioConstants.OBJECT_NAME, "CamelUnitTest3");
            exchange1.getIn().setBody("This is my bucket content.");
        });

        MockEndpoint.assertIsSatisfied(context);

        assertResultExchange(result.getExchanges().get(0));

        assertResponseMessage(exchange.getMessage());
    }

    private void assertResultExchange(Exchange resultExchange) {
        assertEquals("This is my bucket content.", resultExchange.getIn().getBody(String.class));
        assertEquals("mycamelbucket", resultExchange.getIn().getHeader(MinioConstants.BUCKET_NAME));
        assertTrue(resultExchange.getIn().getHeader(MinioConstants.OBJECT_NAME, String.class).startsWith("CamelUnitTest"));
        assertNull(resultExchange.getIn().getHeader(MinioConstants.VERSION_ID)); // not enabled on this bucket
        assertNotNull(resultExchange.getIn().getHeader(MinioConstants.LAST_MODIFIED));
        assertEquals("application/octet-stream", resultExchange.getIn().getHeader(MinioConstants.CONTENT_TYPE));
        assertNull(resultExchange.getIn().getHeader(MinioConstants.CONTENT_ENCODING));
        assertEquals(26L, resultExchange.getIn().getHeader(MinioConstants.CONTENT_LENGTH));
        assertNull(resultExchange.getIn().getHeader(MinioConstants.CONTENT_DISPOSITION));
        assertNull(resultExchange.getIn().getHeader(MinioConstants.CONTENT_MD5));
        assertNull(resultExchange.getIn().getHeader(MinioConstants.CACHE_CONTROL));
    }

    private void assertResponseMessage(Message message) {
        assertNull(message.getHeader(MinioConstants.VERSION_ID));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String minioEndpointUri
                        = "minio://mycamelbucket?accessKey=" + service.accessKey()
                          + "&secretKey=RAW(" + service.secretKey()
                          + ")&autoCreateBucket=true&endpoint=http://" + service.host() + "&proxyPort="
                          + service.port();
                from("direct:start").to(minioEndpointUri);
                from(minioEndpointUri).to("mock:result");

            }
        };
    }
}
