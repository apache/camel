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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test to verify the append option
 */
public class FileProduceAppendTest extends ContextTestSupport {

    public void testAppendText() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedFileExists("target/test-file-append/hello.txt", "Hello World");

        template.sendBody("direct:start", " World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/test-file-append");
        super.setUp();
        template.sendBodyAndHeader("file://target/test-file-append", "Hello", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader("file://target/test-file-append", " World", Exchange.FILE_NAME, "world.txt");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .setHeader(Exchange.FILE_NAME, constant("hello.txt"))
                    .to("file://target/test-file-append?fileExist=Append", "mock:result");
            }
        };
    }

}