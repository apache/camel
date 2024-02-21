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
package org.apache.camel.component.undertow;

import org.apache.camel.builder.RouteBuilder;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class UndertowComponentMuteExceptionTest extends BaseUndertowTest {

    @Test
    public void muteExceptionTest() throws Exception {
        HttpGet get = new HttpGet("http://localhost:" + getPort() + "/test/mute");
        get.addHeader("Accept", "application/text");
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(get)) {

            String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
            assertNotNull(responseString);
            assertEquals("", responseString);
            assertEquals(500, response.getCode());
        }
    }

    @Test
    public void muteExceptionWithTransferExceptionTest() throws Exception {

        HttpGet get = new HttpGet("http://localhost:" + getPort() + "/test/muteWithTransfer");
        get.addHeader("Accept", "application/text");

        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(get)) {

            String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
            assertNotNull(responseString);
            assertEquals("", responseString);

            assertEquals(500, response.getCode());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            public void configure() {
                UndertowComponent uc = context.getComponent("undertow", UndertowComponent.class);
                uc.setMuteException(true);

                from("undertow:http://localhost:" + getPort() + "/test/mute").to("mock:input")
                        .throwException(new IllegalArgumentException("Camel cannot do this"));

                from("undertow:http://localhost:" + getPort()
                     + "/test/muteWithTransfer?transferException=true").to("mock:input")
                        .throwException(new IllegalArgumentException("Camel cannot do this"));
            }
        };
    }

}
