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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.netty.http.RawHttpClient.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.apache.camel.component.netty.http.RawHttpClient.chunkedPost;
import static org.apache.camel.component.netty.http.RawHttpClient.get;
import static org.apache.camel.component.netty.http.RawHttpClient.post;
import static org.apache.camel.component.netty.http.RawHttpClient.send;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A request with a chunked body (Transfer-Encoding: chunked, as sent by HTTP clients for a body of unknown length) to a
 * shared Netty HTTP server is answered, whether the route reads the body or not, and whether a route matches or not.
 * The server has one I/O thread, so a request that is not answered would also hold the requests after it.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class NettySharedHttpServerChunkedRequestTest extends BaseNettyTestSupport {

    private NettySharedHttpServer nettySharedHttpServer;

    @BindToRegistry("myNettyServer")
    public NettySharedHttpServer createServer() {
        nettySharedHttpServer = new DefaultNettySharedHttpServer();
        nettySharedHttpServer.setCamelContext(context);

        NettySharedHttpServerBootstrapConfiguration configuration = new NettySharedHttpServerBootstrapConfiguration();
        configuration.setPort(getPort());
        configuration.setHost("localhost");
        configuration.setWorkerCount(1);
        configureServer(configuration);
        nettySharedHttpServer.setNettyServerBootstrapConfiguration(configuration);

        nettySharedHttpServer.start();

        return nettySharedHttpServer;
    }

    protected void configureServer(NettySharedHttpServerBootstrapConfiguration configuration) {
        // the default configuration (chunked = true)
    }

    @Override
    public void doPostTearDown() {
        nettySharedHttpServer.stop();
    }

    @Test
    void testChunkedRequestRouteReadsBody() throws Exception {
        getMockEndpoint("mock:read").expectedBodiesReceived("Hello World");

        Response response = send(getPort(), chunkedPost("/read", "Hello ", "World"));
        assertEquals("HTTP/1.1 200 OK", response.status());
        assertEquals("Bye World", response.body());

        assertEquals("HTTP/1.1 200 OK", send(getPort(), get("/hello")).status());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testChunkedRequestRouteDoesNotReadBody() throws Exception {
        Response response = send(getPort(), chunkedPost("/noread", 4));
        assertEquals("HTTP/1.1 200 OK", response.status());
        assertEquals("ok", response.body());

        assertEquals("HTTP/1.1 200 OK", send(getPort(), get("/hello")).status());
    }

    @Test
    void testChunkedRequestUnknownPath() throws Exception {
        assertEquals("HTTP/1.1 404 Not Found", send(getPort(), chunkedPost("/unknown", 4)).status());

        assertEquals("HTTP/1.1 200 OK", send(getPort(), get("/hello")).status());
    }

    @Test
    void testRequestWithContentLength() throws Exception {
        getMockEndpoint("mock:read").expectedBodiesReceived("Hello World");

        Response response = send(getPort(), post("/read", "Hello World"));
        assertEquals("HTTP/1.1 200 OK", response.status());
        assertEquals("Bye World", response.body());

        Response hello = send(getPort(), get("/hello"));
        assertEquals("HTTP/1.1 200 OK", hello.status());
        assertEquals("hello", hello.body());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testStreamedResponse() throws Exception {
        Response response = send(getPort(), get("/stream"));
        assertEquals("HTTP/1.1 200 OK", response.status());
        assertEquals("chunked", response.headers().get("transfer-encoding"));
        assertEquals("x".repeat(20000), response.body());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty-http:http://localhost/read?nettySharedHttpServer=#myNettyServer")
                        .convertBodyTo(String.class)
                        .to("mock:read")
                        .transform().constant("Bye World");

                from("netty-http:http://localhost/noread?nettySharedHttpServer=#myNettyServer")
                        .noStreamCaching()
                        .transform().constant("ok");

                from("netty-http:http://localhost/hello?nettySharedHttpServer=#myNettyServer")
                        .transform().constant("hello");

                from("netty-http:http://localhost/stream?nettySharedHttpServer=#myNettyServer&disableStreamCache=true")
                        .process(e -> e.getMessage().setBody(
                                new ByteArrayInputStream("x".repeat(20000).getBytes(StandardCharsets.US_ASCII))));
            }
        };
    }
}
