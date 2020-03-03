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
package org.apache.camel.component.file.remote;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertDirectoryEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class FtpConsumerMultipleDirectoriesTest extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/multidir/?password=admin&recursive=true&delay=5000&sortBy=file:path";
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        prepareFtpServer();
    }

    @Test
    public void testMultiDir() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World", "Hello World", "Goodday World");

        assertMockEndpointsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        RemoteFile<?> file = (RemoteFile<?>)exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
        assertNotNull(file);
        assertDirectoryEquals("multidir/bye.txt", file.getAbsoluteFilePath());
        assertDirectoryEquals("bye.txt", file.getRelativeFilePath());
        assertEquals("bye.txt", file.getFileName());

        exchange = mock.getExchanges().get(1);
        file = (RemoteFile<?>)exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
        assertNotNull(file);
        assertDirectoryEquals("multidir/sub/hello.txt", file.getAbsoluteFilePath());
        assertDirectoryEquals("sub/hello.txt", file.getRelativeFilePath());
        assertEquals("sub/hello.txt", file.getFileName());
        assertEquals("hello.txt", file.getFileNameOnly());

        exchange = mock.getExchanges().get(2);
        file = (RemoteFile<?>)exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
        assertNotNull(file);
        assertDirectoryEquals("multidir/sub/sub2/godday.txt", file.getAbsoluteFilePath());
        assertDirectoryEquals("sub/sub2/godday.txt", file.getRelativeFilePath());
        assertEquals("sub/sub2/godday.txt", file.getFileName());
        assertEquals("godday.txt", file.getFileNameOnly());
    }

    private void prepareFtpServer() throws Exception {
        sendFile(getFtpUrl(), "Bye World", "bye.txt");
        sendFile(getFtpUrl(), "Hello World", "sub/hello.txt");
        sendFile(getFtpUrl(), "Goodday World", "sub/sub2/godday.txt");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(getFtpUrl()).to("mock:result");
            }
        };
    }
}
