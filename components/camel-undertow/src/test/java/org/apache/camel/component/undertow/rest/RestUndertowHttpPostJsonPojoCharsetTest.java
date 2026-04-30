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
package org.apache.camel.component.undertow.rest;

import java.nio.charset.StandardCharsets;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test that UTF-8 characters in JSON body are handled correctly even when the request Content-Type does not explicitly
 * specify a charset (e.g. "application/json" without "; charset=UTF-8").
 */
public class RestUndertowHttpPostJsonPojoCharsetTest extends BaseUndertowTest {

    @Test
    public void testPostPojoWithUtf8BodyNoExplicitCharset() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:input");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(UserPojo.class);

        // JSON body with non-ASCII UTF-8 characters
        String body = "{\"id\": 123, \"name\": \"Dönäld Dück\"}";

        String url = "http://localhost:" + getPort() + "/users/new";
        HttpPost httpPost = new HttpPost(url);
        // Send UTF-8 encoded bytes with Content-Type: application/json (NO charset parameter).
        // Using ContentType.create() instead of ContentType.APPLICATION_JSON because the latter
        // includes "charset=UTF-8" which would mask the bug we are testing.
        byte[] utf8Bytes = body.getBytes(StandardCharsets.UTF_8);
        httpPost.setEntity(new ByteArrayEntity(utf8Bytes, ContentType.create("application/json")));

        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(httpPost)) {
            assertEquals(200, response.getCode());
        }

        MockEndpoint.assertIsSatisfied(context);

        UserPojo user = mock.getReceivedExchanges().get(0).getIn().getBody(UserPojo.class);
        assertNotNull(user);
        assertEquals(123, user.getId());
        assertEquals("Dönäld Dück", user.getName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().component("undertow").host("localhost").port(getPort())
                        .bindingMode(RestBindingMode.auto);

                rest("/users/")
                        .post("new").type(UserPojo.class)
                        .to("mock:input");
            }
        };
    }

}
