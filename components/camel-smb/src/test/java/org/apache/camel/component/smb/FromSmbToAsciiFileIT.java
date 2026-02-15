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
package org.apache.camel.component.smb;

import java.io.File;
import java.nio.file.Path;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.TestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class FromSmbToAsciiFileIT extends SmbServerTestSupport {

    @TempDir
    Path testDirectory;

    protected String getSmbUrl() {
        return String.format(
                "smb:%s/%s/toasciifile?username=%s&password=%s&fileExist=Override",
                service.address(), service.shareName(), service.userName(), service.password());
    }

    @Override
    public void doPostSetup() throws Exception {
        prepareSmbServer();
    }

    @Test
    public void testSmbRoute() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMinimumMessageCount(1);
        resultEndpoint.expectedBodiesReceived("Hello World from SMBServer");

        resultEndpoint.assertIsSatisfied();

        // assert the file
        File file = testDirectory.resolve("deleteme.txt").toFile();
        assertTrue(file.exists(), "The ASCII file should exists");
        assertTrue(file.length() > 10, "File size wrong");
    }

    private void prepareSmbServer() throws Exception {
        // prepares the SMB Server by creating a file on the server that we want
        // to unit test that we can pool and store as a local file
        Endpoint endpoint = context.getEndpoint(getSmbUrl());
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello World from SMBServer");
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
                from(getSmbUrl()).setHeader(Exchange.FILE_NAME, constant("deleteme.txt")).convertBodyTo(String.class)
                        .to(TestSupport.fileUri(testDirectory, "?fileExist=Override&noop=true")).to("mock:result");
            }
        };
    }
}
