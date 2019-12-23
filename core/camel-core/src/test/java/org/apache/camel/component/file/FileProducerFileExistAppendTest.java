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

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

public class FileProducerFileExistAppendTest extends ContextTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/file");
        super.setUp();
    }

    @Test
    public void testAppend() throws Exception {
        template.sendBodyAndHeader("file://target/data/file", "Hello World\n", Exchange.FILE_NAME, "hello.txt");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World\nBye World");
        mock.expectedFileExists("target/data/file/hello.txt", "Hello World\nBye World");

        template.sendBodyAndHeader("file://target/data/file?fileExist=Append", "Bye World", Exchange.FILE_NAME, "hello.txt");

        context.getRouteController().startAllRoutes();

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testAppendFileByFile() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");

        // Create some test files
        template.sendBodyAndHeader("file://target/data/file", "Row 1\n", Exchange.FILE_NAME, "test1.txt");
        template.sendBodyAndHeader("file://target/data/file", "Row 2\n", Exchange.FILE_NAME, "test2.txt");

        // Append test files to the target one
        template.sendBodyAndHeader("file://target/data/file?fileExist=Append", new File("target/data/file/test1.txt"), Exchange.FILE_NAME, "out.txt");
        template.sendBodyAndHeader("file://target/data/file?fileExist=Append", new File("target/data/file/test2.txt"), Exchange.FILE_NAME, "out.txt");

        mock.expectedFileExists("target/data/file/out.txt", "Row 1\nRow 2\n");

        context.getRouteController().startAllRoutes();

        assertMockEndpointsSatisfied();

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/data/file?noop=true&initialDelay=0&delay=10").noAutoStartup().convertBodyTo(String.class).to("mock:result");
            }
        };
    }
}
