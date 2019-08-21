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

/**
 * Unit test for consuming multiple directories.
 */
public class FileConsumeMultipleDirectoriesTest extends ContextTestSupport {

    private String fileUrl = "file://target/data/multidir/?initialDelay=0&delay=10&recursive=true&delete=true&sortBy=file:path";

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/multidir");
        super.setUp();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testMultiDir() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World", "Hello World", "Godday World");

        template.sendBodyAndHeader(fileUrl, "Bye World", Exchange.FILE_NAME, "bye.txt");
        template.sendBodyAndHeader(fileUrl, "Hello World", Exchange.FILE_NAME, "sub/hello.txt");
        template.sendBodyAndHeader(fileUrl, "Godday World", Exchange.FILE_NAME, "sub/sub2/godday.txt");

        assertMockEndpointsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        GenericFile<File> gf = (GenericFile<File>)exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
        File file = gf.getFile();
        assertDirectoryEquals("target/data/multidir/bye.txt", file.getPath());
        assertEquals("bye.txt", file.getName());

        exchange = mock.getExchanges().get(1);
        gf = (GenericFile<File>)exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
        file = gf.getFile();
        assertDirectoryEquals("target/data/multidir/sub/hello.txt", file.getPath());
        assertEquals("hello.txt", file.getName());

        exchange = mock.getExchanges().get(2);
        gf = (GenericFile<File>)exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
        file = gf.getFile();
        assertDirectoryEquals("target/data/multidir/sub/sub2/godday.txt", file.getPath());
        assertEquals("godday.txt", file.getName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(fileUrl).convertBodyTo(String.class).to("mock:result");
            }
        };
    }

}
