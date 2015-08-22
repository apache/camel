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
package org.apache.camel.component.netty4.http;

import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

import static org.apache.camel.Exchange.HTTP_METHOD;

public class NettyHttpRestContextPathMatcherTest extends BaseNettyTest {

    @Test
    public void shouldReturnCustomResponseForOptions() throws Exception {
        String response = template.requestBodyAndHeader("netty4-http:http://localhost:{{port}}/foo", "", HTTP_METHOD, "OPTIONS", String.class);
        assertEquals("expectedOptionsResponse", response);
    }

    @Test
    public void shouldPreferStrictMatchOverPrefixMatch() throws Exception {
        String response = template.requestBodyAndHeader("netty4-http:http://localhost:{{port}}/path2/foo", "", HTTP_METHOD, "GET", String.class);
        assertEquals("exact", response);
    }

    @Test
    public void shouldPreferOptionsForEqualPaths() throws Exception {
        String response = template.requestBodyAndHeader("netty4-http:http://localhost:{{port}}/path3", "", HTTP_METHOD, "POST", String.class);
        assertEquals("postPath3", response);
        response = template.requestBodyAndHeader("netty4-http:http://localhost:{{port}}/path3", "", HTTP_METHOD, "OPTIONS", String.class);
        assertEquals("optionsPath3", response);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty4-http:http://0.0.0.0:{{port}}/path1?httpMethodRestrict=POST").setBody().constant("somePostResponse");
                from("netty4-http:http://0.0.0.0:{{port}}?matchOnUriPrefix=true&httpMethodRestrict=OPTIONS").setBody().constant("expectedOptionsResponse");

                from("netty4-http:http://0.0.0.0:{{port}}/path2/foo").setBody().constant("exact");
                from("netty4-http:http://0.0.0.0:{{port}}/path2?matchOnUriPrefix=true").setBody().constant("wildcard");

                from("netty4-http:http://0.0.0.0:{{port}}/path3?httpMethodRestrict=POST").setBody().constant("postPath3");
                from("netty4-http:http://0.0.0.0:{{port}}/path3?httpMethodRestrict=OPTIONS").setBody().constant("optionsPath3");
            }
        };
    }

}
