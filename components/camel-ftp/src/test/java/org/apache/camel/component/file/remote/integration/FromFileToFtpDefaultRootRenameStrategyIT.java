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
import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FromFileToFtpDefaultRootRenameStrategyIT extends FtpServerTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        prepareFtpServer();
    }

    /*
     * This is our poll we want to test (no folder specified). Uses the rename
     * strategy
     */
    private String getFtpPollingUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}"
               + "?password=admin&delete=true&binary=true&delay=1000&initialDelay=1500&readLock=rename";
    }

    /*
     * we use this URL to write out our binary test file to begin with
     */
    private String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}?password=admin&binary=true";
    }

    @Test
    public void testFromFileToFtp() throws Exception {
        File expectedOnFtpServer = service.ftpFile("logo.jpeg").toFile();
        // the poller won't start for 1.5 seconds, so we check to make sure the
        // file
        // is there first check 1 - is the file there (default root location)
        assertTrue(expectedOnFtpServer.exists());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        MockEndpoint.assertIsSatisfied(context);

        await().atMost(250, TimeUnit.MILLISECONDS).untilAsserted(() -> assertFalse(expectedOnFtpServer.exists()));
    }

    private void prepareFtpServer() throws Exception {
        // create a binary file .. uploaded to the default root location
        Endpoint endpoint = context.getEndpoint(getFtpUrl());
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody(new File("src/test/data/ftpbinarytest/logo.jpeg"));
        exchange.getIn().setHeader(Exchange.FILE_NAME, "logo.jpeg");
        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);
        producer.stop();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(getFtpPollingUrl()).to("mock:result");
            }
        };
    }

}
