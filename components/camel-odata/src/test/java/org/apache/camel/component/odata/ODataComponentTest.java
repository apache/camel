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
package org.apache.camel.component.odata;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ODataComponentTest extends CamelTestSupport {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String baseUrl = "http://localhost:" + wireMock.getPort() + "/odata";

                from("direct:readSet")
                        .to("odata:" + baseUrl + "/Products");

                from("direct:readEntry")
                        .to("odata:" + baseUrl + "/Products(1)");

                from("direct:create")
                        .to("odata:" + baseUrl + "/Products?operation=CREATE");

                from("direct:delete")
                        .to("odata:" + baseUrl + "/Products(3)?operation=DELETE");
            }
        };
    }

    @Test
    void testReadSetWithMetadata() {
        wireMock.stubFor(get(urlPathEqualTo("/odata/Products"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{"
                                  + "\"@odata.count\": 10,"
                                  + "\"@odata.nextLink\": \"http://localhost:" + wireMock.getPort()
                                  + "/odata/Products?$skip=2\","
                                  + "\"value\": ["
                                  + "  {\"ID\": 1, \"Name\": \"Widget\"},"
                                  + "  {\"ID\": 2, \"Name\": \"Gadget\"}"
                                  + "]}")));

        Exchange exchange = template.request("direct:readSet", ex -> {
            ex.getMessage().setHeader(ODataConstants.TOP, 2);
        });

        Map<?, ?> body = exchange.getMessage().getBody(Map.class);
        assertNotNull(body);

        assertEquals(10L, exchange.getMessage().getHeader(ODataConstants.COUNT));
        assertEquals("http://localhost:" + wireMock.getPort() + "/odata/Products?$skip=2",
                exchange.getMessage().getHeader(ODataConstants.NEXT_LINK));
    }

    @Test
    void testReadSingleEntity() {
        wireMock.stubFor(get(urlPathEqualTo("/odata/Products(1)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ID\": 1, \"Name\": \"Widget\"}")));

        Exchange exchange = template.request("direct:readEntry", ex -> {
            ex.getMessage().setHeader(ODataConstants.OPERATION, ODataOperation.READ_ENTRY);
        });

        Map<?, ?> body = exchange.getMessage().getBody(Map.class);
        assertNotNull(body);
        assertEquals("Widget", body.get("Name"));
    }

    @Test
    void testCreateEntity() {
        wireMock.stubFor(post(urlPathEqualTo("/odata/Products"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ID\": 3, \"Name\": \"NewProduct\"}")));

        Map<String, Object> payload = new HashMap<>();
        payload.put("Name", "NewProduct");

        Exchange exchange = template.request("direct:create", ex -> ex.getMessage().setBody(payload));

        assertEquals(201, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        Map<?, ?> body = exchange.getMessage().getBody(Map.class);
        assertEquals(new BigDecimal(3), body.get("ID"));
    }

    @Test
    void testDeleteEntity() {
        wireMock.stubFor(delete(urlPathEqualTo("/odata/Products(3)"))
                .willReturn(aResponse().withStatus(204)));

        Exchange exchange = template.request("direct:delete", ex -> {
        });

        assertEquals(204, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertNull(exchange.getMessage().getBody());
    }
}
