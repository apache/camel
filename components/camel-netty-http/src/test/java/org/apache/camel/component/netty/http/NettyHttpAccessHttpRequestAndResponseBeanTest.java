/**
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

import java.nio.charset.Charset;

import org.apache.camel.builder.RouteBuilder;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.junit.Test;

public class NettyHttpAccessHttpRequestAndResponseBeanTest extends BaseNettyTest {

    @Test
    public void testRawHttpRequestAndResponseInBean() throws Exception {
        getMockEndpoint("mock:input").expectedBodiesReceived("World", "Camel");

        String out = template.requestBody("netty-http:http://localhost:{{port}}/foo", "World", String.class);
        assertEquals("Bye World", out);

        String out2 = template.requestBody("netty-http:http://localhost:{{port}}/foo", "Camel", String.class);
        assertEquals("Bye Camel", out2);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty-http:http://0.0.0.0:{{port}}/foo")
                    .to("mock:input")
                    .transform().method(NettyHttpAccessHttpRequestAndResponseBeanTest.class, "myTransformer");
            }
        };
    }

    /**
     * We can use both a netty http request and response type for transformation
     */
    public static HttpResponse myTransformer(HttpRequest request) {
        String in = request.getContent().toString(Charset.forName("UTF-8"));
        String reply = "Bye " + in;

        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.setContent(ChannelBuffers.copiedBuffer(reply.getBytes()));
        response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, reply.length());

        return response;
    }

}
