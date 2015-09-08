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
package org.apache.camel.component.http;

import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.junit.Before;

public class HttpGetWithHeadersTest extends HttpGetTest {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .setHeader("TestHeader", constant("test"))
                    .setHeader("Content-Length", constant(0))
                    .setHeader("Accept-Language", constant("pl"))
                    .to("http://www.google.com/search")
                    .to("mock:results");
            }
        };
    }

    @Override
    @Before
    public void setUp() throws Exception {
        // "Szukaj" is "Search" in polish language
        expectedText = "Szukaj";
        super.setUp();
    }

    @Override
    protected void checkHeaders(Map<String, Object> headers) {
        assertTrue("Should be more than one header but was: " + headers, headers.size() > 0);
        assertEquals("Should get the TestHeader", "test", headers.get("TestHeader"));
    }

}
