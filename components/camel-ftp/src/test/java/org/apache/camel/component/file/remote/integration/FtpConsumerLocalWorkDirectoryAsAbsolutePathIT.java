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
import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.TestSupport;
import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.apache.camel.test.junit5.TestSupport.assertFileExists;
import static org.apache.camel.test.junit5.TestSupport.assertFileNotExists;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FtpConsumerLocalWorkDirectoryAsAbsolutePathIT extends FtpServerTestSupport {
    @TempDir
    Path testDirectory;

    protected String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}/lwd/?password=admin&delay=5000&noop=true&localWorkDirectory="
               + testDirectory.toAbsolutePath();
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        prepareFtpServer();
    }

    @Test
    public void testLocalWorkDirectory() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedMessageCount(1);

        MockEndpoint.assertIsSatisfied(context);

        // give test some time to close file resources
        // now the lwd file should be deleted
        await().atMost(6, TimeUnit.SECONDS)
                .untilAsserted(() -> assertFileNotExists(testDirectory.resolve("hello.txt")));

        // and the out file should exists
        assertFileExists(testDirectory.resolve("out/hello.txt"), "Hello World");
    }

    private void prepareFtpServer() throws Exception {
        // prepares the FTP Server by creating a file on the server that we want
        // to unit
        // test that we can pool
        Endpoint endpoint = context.getEndpoint(getFtpUrl());
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello World");
        exchange.getIn().setHeader(Exchange.FILE_NAME, "hello.txt");
        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);
        producer.stop();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(getFtpUrl()).process(new Processor() {
                    public void process(Exchange exchange) {
                        File body = exchange.getIn().getBody(File.class);
                        assertNotNull(body);
                        assertTrue(body.isAbsolute(), "Should be absolute path");
                        assertTrue(body.exists(), "Local work file should exists");
                        assertEquals(FileUtil.normalizePath(testDirectory.resolve("hello.txt").toString()), body.getPath());
                    }
                }).to("mock:result", TestSupport.fileUri(testDirectory, "out"));
            }
        };
    }
}
