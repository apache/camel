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
package org.apache.camel.component.http;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.component.http.handler.BasicValidationHandler;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.http.HttpMethods.POST;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A Map or List body cannot be an HTTP request body; the error says to marshal it to JSON first (CAMEL-25309).
 */
public class HttpProducerMapBodyTest extends BaseHttpTest {

    private HttpServer localServer;
    private String endpointUrl;

    @Override
    public void setupResources() throws Exception {
        localServer = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost").setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .register("/post", new BasicValidationHandler(POST.name(), null, null, getExpectedContent()))
                .create();
        localServer.start();
        endpointUrl = "http://localhost:" + localServer.getLocalPort();
    }

    @Override
    public void cleanupResources() throws Exception {
        if (localServer != null) {
            localServer.stop();
        }
    }

    @Test
    public void aMapBodySaysToMarshalItToJson() {
        Exchange exchange = template.request(endpointUrl + "/post?httpMethod=POST",
                // the body after unmarshal: json with Jackson
                e -> e.getIn().setBody(new LinkedHashMap<>(Map.of("orderId", "ORD-1001", "qty", 2))));

        assertThat(exchange.getException()).isInstanceOf(InvalidPayloadException.class)
                .hasMessageContaining("java.util.LinkedHashMap").hasMessageContaining("a Map is not an HTTP request body")
                .hasMessageContaining("marshal it to JSON first (marshal: json)");
    }

    @Test
    public void aListBodySaysToMarshalItToJson() {
        Exchange exchange = template.request(endpointUrl + "/post?httpMethod=POST",
                e -> e.getIn().setBody(List.of(Map.of("sku", "CAMEL-MUG"))));

        assertThat(exchange.getException()).isInstanceOf(InvalidPayloadException.class)
                .hasMessageContaining("a List is not an HTTP request body");
    }
}
