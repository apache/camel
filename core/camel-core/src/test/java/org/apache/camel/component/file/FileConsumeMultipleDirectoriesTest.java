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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for consuming multiple directories.
 */
public class FileConsumeMultipleDirectoriesTest extends ContextTestSupport {

    public static final String FILE_QUERY = "?initialDelay=0&delay=10&recursive=true&delete=true&sortBy=file:path";

    @SuppressWarnings("unchecked")
    @Test
    public void testMultiDir() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World", "Hello World", "Godday World");
        String fileUri = fileUri(FILE_QUERY);
        template.sendBodyAndHeader(fileUri, "Bye World", Exchange.FILE_NAME, "bye.txt");
        template.sendBodyAndHeader(fileUri, "Hello World", Exchange.FILE_NAME, "sub/hello.txt");
        template.sendBodyAndHeader(fileUri, "Godday World", Exchange.FILE_NAME, "sub/sub2/godday.txt");

        assertMockEndpointsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        GenericFile<File> gf = (GenericFile<File>) exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
        File file = gf.getFile();
        assertDirectoryEquals(testFile("bye.txt").toString(), file.getPath());
        assertEquals("bye.txt", file.getName());

        exchange = mock.getExchanges().get(1);
        gf = (GenericFile<File>) exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
        file = gf.getFile();
        assertDirectoryEquals(testFile("sub/hello.txt").toString(), file.getPath());
        assertEquals("hello.txt", file.getName());

        exchange = mock.getExchanges().get(2);
        gf = (GenericFile<File>) exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
        file = gf.getFile();
        assertDirectoryEquals(testFile("sub/sub2/godday.txt").toString(), file.getPath());
        assertEquals("godday.txt", file.getName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(fileUri(FILE_QUERY)).convertBodyTo(String.class).to("mock:result");
            }
        };
    }

}
