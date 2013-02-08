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
package org.apache.camel.component.file.remote;

import java.io.File;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test to test noop and idempotent option.
 */
public class FromFtpNoopIdempotentFalseTest extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/noop?password=admin&binary=false&noop=true&idempotent=false";
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        prepareFtpServer();
    }

    @Test
    public void testNoop() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        // we should be able to poll the same file multiple times
        mock.expectedMinimumMessageCount(2);

        mock.assertIsSatisfied();

        // assert the file is still there
        File file = new File(FTP_ROOT_DIR + "/noop/hello.txt");
        assertTrue("The file should exists", file.exists());
    }

    private void prepareFtpServer() throws Exception {
        // prepares the FTP Server by creating a file on the server that we want to unit
        // test that we can pool and store as a local file
        Endpoint endpoint = context.getEndpoint(getFtpUrl());
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello World");
        exchange.getIn().setHeader(Exchange.FILE_NAME, "hello.txt");
        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);
        producer.stop();
    }
    
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(getFtpUrl()).to("log:result", "mock:result");
            }
        };
    }
}