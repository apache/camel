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
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.spi.EndpointUriFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NettyHttpEndpointUriAssemblerTest extends CamelTestSupport {

    @Test
    public void testAsEndpointUriNettyHttpHostnameWithDash() throws Exception {
        EndpointUriFactory assembler = context.getCamelContextExtension().getEndpointUriFactory("netty-http");

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("protocol", "http");
        map.put("host", "a-b-c.hostname.tld");
        map.put("port", "8080");
        map.put("path", "anything");
        String uri = assembler.buildUri("netty-http", map);
        assertEquals("netty-http:http://a-b-c.hostname.tld:8080/anything", uri);

        map = new LinkedHashMap<>();
        map.put("protocol", "http");
        map.put("host", "a-b-c.server.net");
        map.put("port", "8888");
        map.put("path", "service/v3");
        uri = assembler.buildUri("netty-http", map);
        assertEquals("netty-http:http://a-b-c.server.net:8888/service/v3", uri);

        map = new HashMap<>();
        // use http protocol
        map.put("protocol", "http");
        map.put("host", "localhost");
        map.put("port", "8080");
        map.put("path", "foo/bar");
        map.put("disconnect", "true");

        uri = assembler.buildUri("netty-http", map);
        assertEquals("netty-http:http://localhost:8080/foo/bar?disconnect=true", uri);

        // lets switch protocol
        map.put("protocol", "https");

        uri = assembler.buildUri("netty-http", map);
        assertEquals("netty-http:https://localhost:8080/foo/bar?disconnect=true", uri);

        // lets set a query parameter in the path
        map.put("path", "foo/bar?verbose=true");
        map.put("disconnect", "true");

        uri = assembler.buildUri("netty-http", map);
        assertEquals("netty-http:https://localhost:8080/foo/bar?verbose=true&disconnect=true", uri);
    }

}
