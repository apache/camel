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
package org.apache.camel.component.file;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test to verify the overrule filename header
 */
public class FileProduceOverruleOnlyOnceTest extends ContextTestSupport {

    public void testBoth() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(Exchange.FILE_NAME, "hello.txt");
        mock.message(0).header(Exchange.OVERRULE_FILE_NAME).isNull();
        mock.expectedFileExists("target/write/ruled.txt", "Hello World");
        mock.expectedFileExists("target/again/hello.txt", "Hello World");

        Map<String, Object> map = new HashMap<String, Object>();
        map.put(Exchange.FILE_NAME, "hello.txt");
        // this header should overrule the endpoint configuration
        map.put(Exchange.OVERRULE_FILE_NAME, "ruled.txt");

        template.sendBodyAndHeaders("direct:start", "Hello World", map);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/write");
        deleteDirectory("target/again");
        super.setUp();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .to("file://target/write")
                    .to("file://target/again", "mock:result");
            }
        };
    }

}