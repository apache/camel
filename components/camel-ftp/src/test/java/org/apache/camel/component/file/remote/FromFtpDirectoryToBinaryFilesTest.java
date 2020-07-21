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

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.IOConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test to verify that we can pool a BINARY file in a directory from the
 * FTP Server and store it on a local file path. Based on CAMEL-834.
 */
public class FromFtpDirectoryToBinaryFilesTest extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/incoming/?password=admin" + "&binary=true&useFixedDelay=false&recursive=false&delay=5000";
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        prepareFtpServer();
    }

    @Test
    public void testFtpRoute() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMessageCount(2);
        resultEndpoint.assertIsSatisfied();

        Exchange ex = resultEndpoint.getExchanges().get(0);
        byte[] bytes = ex.getIn().getBody(byte[].class);
        assertTrue(bytes.length > 10000, "Logo size wrong");

        // assert the file
        File file = new File("target/ftptest/logo1.jpeg");
        assertTrue(file.exists(), "The binary file should exists");
        assertTrue(file.length() > 10000, "Logo size wrong");

        // assert the file
        file = new File("target/ftptest/logo.jpeg");
        assertTrue(file.exists(), " The binary file should exists");
        assertTrue(file.length() > 10000, "Logo size wrong");
    }

    private void prepareFtpServer() throws Exception {
        // prepares the FTP Server by creating a file on the server that we want
        // to unit
        // test that we can pool and store as a local file
        template.sendBodyAndHeader(getFtpUrl(), IOConverter.toFile("src/test/data/ftpbinarytest/logo.jpeg"), Exchange.FILE_NAME, "logo.jpeg");
        template.sendBodyAndHeader(getFtpUrl(), IOConverter.toFile("src/test/data/ftpbinarytest/logo1.jpeg"), Exchange.FILE_NAME, "logo1.jpeg");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                String fileUrl = "file:target/ftptest/?noop=true";

                from(getFtpUrl()).to(fileUrl, "mock:result");
            }
        };
    }
}
