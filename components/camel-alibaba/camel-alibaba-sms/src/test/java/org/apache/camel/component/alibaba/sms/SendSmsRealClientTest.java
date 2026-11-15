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
package org.apache.camel.component.alibaba.sms;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.teaopenapi.models.Config;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * sendSms with the Short Message Service client of the SDK, not a mock, against a local HTTP server. tea-openapi fails
 * with a NoSuchFieldError when the build resolves a tea-util older than the one it declares (CAMEL-26972).
 */
class SendSmsRealClientTest extends CamelTestSupport {

    private final TestConfiguration testConfiguration = new TestConfiguration();
    private final List<String> actions = new CopyOnWriteArrayList<>();
    private HttpServer server;

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            actions.add(exchange.getRequestHeaders().getFirst("x-acs-action"));
            byte[] answer = "{\"Code\":\"OK\",\"Message\":\"OK\",\"BizId\":\"biz-123\",\"RequestId\":\"req-456\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, answer.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(answer);
            }
        });
        server.start();
    }

    @Override
    protected void cleanupResources() throws Exception {
        if (server != null) {
            server.stop(0);
        }
        super.cleanupResources();
    }

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        // plain HTTP, so that the local server needs no TLS setup
        Config config = new Config()
                .setAccessKeyId(testConfiguration.getProperty("accessKey"))
                .setAccessKeySecret(testConfiguration.getProperty("secretKey"))
                .setRegionId(testConfiguration.getProperty("region"))
                .setProtocol("HTTP")
                .setEndpoint("127.0.0.1:" + server.getAddress().getPort());
        registry.bind("smsClient", new Client(config));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:send")
                        .to("alibaba-sms:sendSms"
                            + "?phoneNumbers=" + testConfiguration.getProperty("phoneNumbers")
                            + "&signName=" + testConfiguration.getProperty("signName")
                            + "&templateCode=" + testConfiguration.getProperty("templateCode")
                            + "&region=" + testConfiguration.getProperty("region")
                            + "&smsClient=#smsClient");
            }
        };
    }

    @Test
    void sendSmsSucceeds() {
        // requestBody throws the exception of the exchange when the exchange fails
        @SuppressWarnings("unchecked")
        Map<String, Object> response = template.requestBody("direct:send", "ignored", Map.class);

        assertThat(response)
                .containsEntry("statusCode", 200)
                .containsEntry("code", "OK");
        assertThat(actions).containsExactly("SendSms");
    }
}
