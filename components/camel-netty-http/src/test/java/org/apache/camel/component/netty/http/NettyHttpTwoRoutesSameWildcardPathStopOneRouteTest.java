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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Two consumers with the same wildcard path (one per HTTP method, or one per port): stopping one of them must not stop
 * the wildcard matching of the other, which is still running.
 */
class NettyHttpTwoRoutesSameWildcardPathStopOneRouteTest extends BaseNettyTestSupport {

    @RegisterExtension
    AvailablePortFinder.Port port2 = AvailablePortFinder.find();

    @Test
    void otherMethodOnSameWildcardPathStillServed() throws Exception {
        assertEquals("post", post(getPort(), "/files/a"));
        assertEquals("get", get(getPort(), "/files/a"));

        context.getRouteController().stopRoute("get");

        assertEquals("post", post(getPort(), "/files/a"));
    }

    @Test
    void sameWildcardPathOnOtherPortStillServed() throws Exception {
        assertEquals("other-port", post(port2.getPort(), "/docs/b"));

        context.getRouteController().stopRoute("docs");

        assertEquals("other-port", post(port2.getPort(), "/docs/b"));
    }

    private String post(int p, String path) {
        return template.requestBodyAndHeader("netty-http:http://localhost:" + p + path + "?throwExceptionOnFailure=false",
                "x", Exchange.HTTP_METHOD, "POST", String.class);
    }

    private String get(int p, String path) {
        return template.requestBodyAndHeader("netty-http:http://localhost:" + p + path + "?throwExceptionOnFailure=false",
                null, Exchange.HTTP_METHOD, "GET", String.class);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty-http:http://0.0.0.0:{{port}}/files/*?httpMethodRestrict=GET").routeId("get")
                        .transform().constant("get");
                from("netty-http:http://0.0.0.0:{{port}}/files/*?httpMethodRestrict=POST").routeId("post")
                        .transform().constant("post");

                from("netty-http:http://0.0.0.0:{{port}}/docs/*").routeId("docs")
                        .transform().constant("first-port");
                from("netty-http:http://0.0.0.0:" + port2.getPort() + "/docs/*").routeId("docs2")
                        .transform().constant("other-port");
                // a catch-all on the second port, started last: it must only get what no other consumer matches
                from("netty-http:http://0.0.0.0:" + port2.getPort() + "/*").routeId("catchAll")
                        .transform().constant("catch-all");
            }
        };
    }
}
