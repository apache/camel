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

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test for consuming multiple directories.
 */
public class FileConsumeMultipleDirectoriesTest extends ContextTestSupport {

    private String fileUrl = "file://target/multidir/?consumer.recursive=true&delete=true&consumer.delay=5000&sortBy=file:path";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        deleteDirectory("target/multidir");
        template.sendBodyAndHeader(fileUrl, "Bye World", FileComponent.HEADER_FILE_NAME, "bye.txt");
        template.sendBodyAndHeader(fileUrl, "Hello World", FileComponent.HEADER_FILE_NAME, "sub/hello.txt");
        template.sendBodyAndHeader(fileUrl, "Godday World", FileComponent.HEADER_FILE_NAME, "sub/sub2/godday.txt");
    }

    public void testMultiDir() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World", "Hello World", "Godday World");

        assertMockEndpointsSatisfied();

        FileExchange exchange = (FileExchange) mock.getExchanges().get(0);
        File file = exchange.getFile();
        assertFilePath("target/multidir/bye.txt", file.getPath());
        assertEquals("bye.txt", file.getName());

        exchange = (FileExchange) mock.getExchanges().get(1);
        file = exchange.getFile();
        assertFilePath("target/multidir/sub/hello.txt", file.getPath());
        assertEquals("hello.txt", file.getName());

        exchange = (FileExchange) mock.getExchanges().get(2);
        file = exchange.getFile();
        assertFilePath("target/multidir/sub/sub2/godday.txt", file.getPath());
        assertEquals("godday.txt", file.getName());
    }

    private static void assertFilePath(String expected, String actual) {
        actual = actual.replaceAll("\\\\", "/");
        assertEquals(expected, actual);
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(fileUrl).to("mock:result");
            }
        };
    }

}