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

package org.apache.camel.component.file;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

/**
 * Unit test to verify the writeFileName option
 */
public class FileProduceOverruleExpressionTest extends ContextTestSupport {
    private static final String TEST_FILE_NAME = "hello." + UUID.randomUUID() + ".txt";
    private static final String TEST_FILE_OVERRULED = "overruled." + UUID.randomUUID() + ".txt";

    @Test
    public void testNoOverrule() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(Exchange.FILE_NAME, TEST_FILE_NAME);
        mock.expectedFileExists(testFile("copy-of-" + TEST_FILE_NAME), "Hello World");

        template.sendBodyAndHeader("direct:start", "Hello World", Exchange.FILE_NAME, TEST_FILE_NAME);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOverrule() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(Exchange.FILE_NAME, TEST_FILE_NAME);
        mock.message(0).header(Exchange.OVERRULE_FILE_NAME).isNull();
        mock.expectedFileExists(testFile("copy-of-" + TEST_FILE_OVERRULED), "Hello World");

        Map<String, Object> map = new HashMap<>();
        map.put(Exchange.FILE_NAME, TEST_FILE_NAME);
        // this header should overrule the endpoint configuration
        map.put(Exchange.OVERRULE_FILE_NAME, TEST_FILE_OVERRULED);

        template.sendBodyAndHeaders("direct:start", "Hello World", map);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").to(fileUri("?fileName=copy-of-${file:name}"), "mock:result");
            }
        };
    }
}
