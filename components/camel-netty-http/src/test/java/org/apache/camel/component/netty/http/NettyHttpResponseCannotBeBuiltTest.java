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

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When no HTTP response can be built from the reply, the consumer answers with a 500 instead of sending nothing and
 * leaving the client waiting for a response.
 */
public class NettyHttpResponseCannotBeBuiltTest extends BaseNettyTestSupport {

    private static final String DETAIL = "the reply cannot be converted";

    @RegisterExtension
    AvailablePortFinder.Port unmutedPort = AvailablePortFinder.find();

    @Test
    public void testMutedFailureResponse() throws Exception {
        Reply reply = get(getPort());
        assertEquals(500, reply.code());
        assertEquals("close", reply.connection());
        assertEquals("", reply.body());
    }

    @Test
    public void testFailureResponseWhenNotMuted() throws Exception {
        Reply reply = get(unmutedPort.getPort());
        assertEquals(500, reply.code());
        assertTrue(reply.body().contains(DETAIL), reply.body());
    }

    private Reply get(int port) throws Exception {
        // before, the consumer sent no response at all, so do not wait for one forever
        RequestConfig config = RequestConfig.custom().setResponseTimeout(Timeout.of(10, TimeUnit.SECONDS)).build();
        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(config).build();
             CloseableHttpResponse response = client.execute(new HttpGet("http://localhost:" + port + "/foo"))) {
            return new Reply(
                    response.getCode(), response.getFirstHeader("Connection").getValue(),
                    EntityUtils.toString(response.getEntity(), "UTF-8"));
        }
    }

    private record Reply(int code, String connection, String body) {
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty-http:http://0.0.0.0:{{port}}/foo")
                        .process(exchange -> exchange.getMessage().setBody(new UnconvertibleBody()));

                from("netty-http:http://0.0.0.0:" + unmutedPort.getPort() + "/foo?muteException=false")
                        .process(exchange -> exchange.getMessage().setBody(new UnconvertibleBody()));
            }
        };
    }

    private static class UnconvertibleBody {
        @Override
        public String toString() {
            throw new IllegalStateException(DETAIL);
        }
    }
}
