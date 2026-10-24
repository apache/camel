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
package org.apache.camel.component.netty.http;

import org.apache.camel.builder.RouteBuilder;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code muteException} is declared once, on the netty configuration, so the component option is the configuration's.
 * Setting it to false on the component, as {@code camel.component.netty-http.mute-exception=false} does through the
 * component configurer, must reach the endpoints.
 */
class NettyHttpComponentMuteExceptionDisabledTest extends BaseNettyTestSupport {

    @Test
    void componentLevelFalseSendsTheException() throws Exception {
        HttpGet get = new HttpGet("http://localhost:" + getPort() + "/foo");
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(get)) {
            String body = EntityUtils.toString(response.getEntity(), "UTF-8");
            assertEquals(500, response.getCode());
            assertTrue(body.contains("Camel cannot do this"), "the exception must be in the response: " + body);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                NettyHttpComponent nhc = context.getComponent("netty-http", NettyHttpComponent.class);
                nhc.getComponentPropertyConfigurer().configure(context, nhc, "muteException", "false", true);

                from("netty-http:http://0.0.0.0:{{port}}/foo")
                        .throwException(new IllegalArgumentException("Camel cannot do this"));
            }
        };
    }
}
