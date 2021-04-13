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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertFileExists;
import static org.apache.camel.test.junit5.TestSupport.assertFileNotExists;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FtpConsumerLocalWorkDirectoryDirectIT extends FtpServerTestSupport {

    protected String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}/lwd/?password=admin&delay=5000"
               + "&localWorkDirectory=" + testDirectory("lwd")
               + "&noop=true";
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        prepareFtpServer();
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

    @Test
    public void testLocalWorkDirectory() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();
        assertTrue(notify.matchesWaitTime(), "Should process one file");

        // and the out file should exists
        assertFileExists(testFile("out/hello.txt"), "Hello World");

        // now the lwd file should be deleted
        assertFileNotExists(testFile("lwd/hello.txt"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(getFtpUrl()).to(fileUri("out"));
            }
        };
    }

}
