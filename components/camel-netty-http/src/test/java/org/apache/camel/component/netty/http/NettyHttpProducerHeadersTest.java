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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Assert;
import org.junit.Test;

public class NettyHttpProducerHeadersTest extends CamelTestSupport {

    private int port;

    @Test
    public void testWithEmptyPath() {
        Map<String, Object> headers = new HashMap<>();
        headers.put(Exchange.HTTP_METHOD, "GET");
        headers.put(Exchange.HTTP_QUERY, "hi=hello");
        String result = template.requestBodyAndHeaders("netty-http:http://localhost:" + port, "", headers, String.class);
        Assert.assertEquals("/", result);
    }

    @Test
    public void testWithSlashPathAndQuery() {
        Map<String, Object> headers = new HashMap<>();
        headers.put(Exchange.HTTP_METHOD, "GET");
        headers.put(Exchange.HTTP_PATH, "/");
        headers.put(Exchange.HTTP_QUERY, "hi=hello");
        String result = template.requestBodyAndHeaders("netty-http:http://localhost:" + port, "", headers, String.class);
        Assert.assertEquals("/", result);
    }

    @Test
    public void testWithFilledPathAndQuery() {
        Map<String, Object> headers = new HashMap<>();
        headers.put(Exchange.HTTP_METHOD, "GET");
        headers.put(Exchange.HTTP_PATH, "some-path");
        headers.put(Exchange.HTTP_QUERY, "hi=hello");
        String result = template.requestBodyAndHeaders("netty-http:http://localhost:" + port, "", headers, String.class);
        Assert.assertEquals("/some-path", result);
    }

    @Test
    public void testWithNoQuery() {
        Map<String, Object> headers = new HashMap<>();
        headers.put(Exchange.HTTP_METHOD, "GET");
        String result = template.requestBodyAndHeaders("netty-http:http://localhost:" + port, "", headers, String.class);
        Assert.assertEquals("/", result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                port = AvailablePortFinder.getNextAvailable();

                from("netty-http:http://localhost:" + port + "?matchOnUriPrefix=true")
                    .setBody(simple("${header." + Exchange.HTTP_URI + "}"));
            }
        };
    }
}
