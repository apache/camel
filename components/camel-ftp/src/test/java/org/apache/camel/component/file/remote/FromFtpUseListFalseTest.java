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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit test to poll a fixed file from the FTP server without using the list
 * command.
 */
public class FromFtpUseListFalseTest extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/nolist/?password=admin"
               + "&stepwise=false&useList=false&ignoreFileNotFoundOrPermissionError=true&fileName=report.txt&delete=true";
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        prepareFtpServer();
    }

    @Test
    public void testUseListFalse() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World from FTPServer");

        // just allow to poll a few more times, but we should only get the file
        // once
        Thread.sleep(1000);

        mock.assertIsSatisfied();
    }

    private void prepareFtpServer() throws Exception {
        // prepares the FTP Server by creating a file on the server that we want
        // to unit
        // test that we can pool and store as a local file
        Endpoint endpoint = context.getEndpoint("ftp://admin@localhost:" + getPort() + "/nolist/?password=admin&binary=false");
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello World from FTPServer");
        exchange.getIn().setHeader(Exchange.FILE_NAME, "report.txt");
        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);
        producer.stop();
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
