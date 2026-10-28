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

import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A text field of a multipart form that occurs several times (for example a group of checkboxes) is mapped to a header
 * with all its values, as a repeated query or form-urlencoded parameter is.
 */
class MultiPartFormRepeatedFieldTest extends BaseJettyTest {

    @Test
    void testRepeatedTextFieldKeepsEveryValue() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:form");
        mock.expectedMessageCount(1);

        HttpPost post = new HttpPost("http://localhost:" + getPort() + "/form");
        post.setEntity(MultipartEntityBuilder.create()
                .addTextBody("tag", "integration")
                .addTextBody("project", "Camel")
                .addTextBody("tag", "routing")
                .build());
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(post)) {
            assertEquals(200, response.getCode());
        }

        MockEndpoint.assertIsSatisfied(context);
        assertEquals(List.of("integration", "routing"), mock.getExchanges().get(0).getIn().getHeader("tag"));
        assertEquals("Camel", mock.getExchanges().get(0).getIn().getHeader("project"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jetty://http://localhost:{{port}}/form")
                        .to("mock:form")
                        .setBody(constant("OK"));
            }
        };
    }
}
