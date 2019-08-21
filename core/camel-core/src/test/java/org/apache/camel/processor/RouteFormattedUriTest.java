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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

public class RouteFormattedUriTest extends ContextTestSupport {

    private String path = "target/data/toformat";
    private String name = "hello.txt";
    private String pattern = ".*txt$";
    private String result = "result";

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/toformat");
        super.setUp();
    }

    @Test
    public void testFormattedUri() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:" + result);
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:start").toF("file://%s?fileName=%s", path, name);

                fromF("file://%s?include=%s", path, pattern).toF("mock:%s", result);
                // END SNIPPET: e1
            }
        };
    }
}
