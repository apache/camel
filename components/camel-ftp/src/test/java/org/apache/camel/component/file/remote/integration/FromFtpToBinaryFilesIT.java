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
package org.apache.camel.component.file.remote.integration;

import java.io.File;
import java.nio.file.Path;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.TestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test to verify that we can pool a BINARY file from the FTP Server and store it on a local file path
 */
public class FromFtpToBinaryFilesIT extends FtpServerTestSupport {
    @TempDir
    Path testDirectory;

    // must user "consumer." prefix on the parameters to the file component
    private String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}/incoming?password=admin&binary=true" + "&delay=2000&recursive=true";
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
        File file = testDirectory.resolve("logo.jpeg").toFile();
        assertTrue(file.exists(), " The binary file should exists");
        assertTrue(file.length() > 10000, "Logo size wrong");

        // assert the file
        file = testDirectory.resolve("a/logo1.jpeg").toFile();
        assertTrue(file.exists(), "The binary file should exists");
        assertTrue(file.length() > 10000, "Logo size wrong");
    }

    private void prepareFtpServer() throws Exception {
        // prepares the FTP Server by creating a file on the server that we want
        // to unit
        // test that we can pool and store as a local file
        String ftpUrl
                = "ftp://admin@localhost:{{ftp.server.port}}/incoming?password=admin&binary=true"
                  + "&delay=2000&recursive=false";
        Endpoint endpoint = context.getEndpoint(ftpUrl);
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody(new File("src/test/data/ftpbinarytest/logo.jpeg"));
        exchange.getIn().setHeader(Exchange.FILE_NAME, "logo.jpeg");
        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);
        producer.stop();

        ftpUrl = "ftp://admin@localhost:{{ftp.server.port}}/incoming/a?password=admin&binary=true"
                 + "&delay=2000&recursive=false";
        endpoint = context.getEndpoint(ftpUrl);
        exchange = endpoint.createExchange();
        exchange.getIn().setBody(new File("src/test/data/ftpbinarytest/logo1.jpeg"));
        exchange.getIn().setHeader(Exchange.FILE_NAME, "logo1.jpeg");
        producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);
        producer.stop();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(getFtpUrl()).to(TestSupport.fileUri(testDirectory, "?noop=true"), "mock:result");
            }
        };
    }
}
