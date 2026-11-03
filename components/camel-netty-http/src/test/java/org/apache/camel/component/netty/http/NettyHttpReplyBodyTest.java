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

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.netty.buffer.ByteBuf;
import org.apache.camel.builder.RouteBuilder;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The consumer writes a reply for a List body, and for the request body sent back as-is with disableStreamCache=true.
 */
public class NettyHttpReplyBodyTest extends BaseNettyTestSupport {

    private final AtomicReference<ByteBuf> requestBody = new AtomicReference<>();

    @Test
    public void testListReply() throws Exception {
        Reply reply = execute(new HttpGet("http://localhost:" + getPort() + "/list"));
        assertEquals(200, reply.code());
        assertEquals("[a, b]", reply.body());
    }

    @Test
    public void testEchoRequestBodyWithDisableStreamCache() throws Exception {
        for (int i = 0; i < 3; i++) {
            Reply reply = execute(post("/echo", "Hello World " + i));
            assertEquals(200, reply.code());
            assertEquals("Hello World " + i, reply.body());
        }
        assertRequestBodyReleased();
    }

    @Test
    public void testEchoRequestBodyFromAnotherThreadWithDisableStreamCache() throws Exception {
        for (int i = 0; i < 3; i++) {
            Reply reply = execute(post("/echoThreads", "Hello World " + i));
            assertEquals(200, reply.code());
            assertEquals("Hello World " + i, reply.body());
        }
        assertRequestBodyReleased();
    }

    private void assertRequestBodyReleased() {
        // the reply writes its own reference of the request buffer, so the buffer is still released once
        await().atMost(10, TimeUnit.SECONDS).until(() -> requestBody.get().refCnt() == 0);
    }

    private HttpPost post(String path, String body) {
        HttpPost post = new HttpPost("http://localhost:" + getPort() + path);
        post.setEntity(new StringEntity(body, ContentType.TEXT_PLAIN));
        return post;
    }

    private Reply execute(HttpUriRequestBase request) throws Exception {
        // before, the consumer sent no response at all, so do not wait for one forever
        RequestConfig config = RequestConfig.custom().setResponseTimeout(Timeout.of(10, TimeUnit.SECONDS)).build();
        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(config).build();
             CloseableHttpResponse response = client.execute(request)) {
            return new Reply(response.getCode(), EntityUtils.toString(response.getEntity(), "UTF-8"));
        }
    }

    private record Reply(int code, String body) {
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty-http:http://0.0.0.0:{{port}}/list")
                        .process(exchange -> exchange.getMessage().setBody(List.of("a", "b")));

                from("netty-http:http://0.0.0.0:{{port}}/echo?disableStreamCache=true")
                        .process(exchange -> requestBody.set(exchange.getMessage().getBody(ByteBuf.class)));

                from("netty-http:http://0.0.0.0:{{port}}/echoThreads?disableStreamCache=true")
                        .process(exchange -> requestBody.set(exchange.getMessage().getBody(ByteBuf.class)))
                        .threads(1);
            }
        };
    }
}
