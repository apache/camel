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

import io.undertow.util.Headers;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class UndertowHttpProxyPreserveHostTest extends BaseUndertowTest {

    @Test
    public void preserveHostFalse() {
        String actual =   template.requestBody("undertow:http://localhost:{{port}}", "&preserveHostHeader=false", String.class);
        String expected = "localhost:" + getPort();
        assertNotEquals(expected, actual);
    }
    @Test
    public void preserveHostTrue() {
        String actual =   template.requestBody("undertow:http://localhost:{{port}}", "&preserveHostHeader=true", String.class);
        String expected = "localhost:" + getPort();
        assertEquals(expected, actual);
    }
    @Test
    public void emptyPreseveHost() {
        String actual =   template.requestBody("undertow:http://localhost:{{port}}", "", String.class);
        String expected = "localhost:" + getPort();
        assertEquals(expected, actual);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("undertow:http://localhost:{{port}}")
                        .toD("undertow:http://localhost:{{port2}}?bridgeEndpoint=true${body}");
                from("undertow:http://localhost:{{port2}}")
                        .setBody(exchange -> exchange.getIn().getHeader(Headers.HOST_STRING));
            }
        };
    }
}
