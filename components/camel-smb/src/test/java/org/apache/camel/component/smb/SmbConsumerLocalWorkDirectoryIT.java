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

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.TestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.apache.camel.test.junit5.TestSupport.assertFileExists;
import static org.apache.camel.test.junit5.TestSupport.assertFileNotExists;
import static org.awaitility.Awaitility.await;

public class SmbConsumerLocalWorkDirectoryIT extends SmbServerTestSupport {

    @TempDir
    Path testDirectory;

    @Override
    public void doPostSetup() throws Exception {
        prepareSmbServer();
    }

    protected String getSmbUrl() {
        return String.format(
                "smb:%s/%s?username=%s&password=%s&path=/localwork&noop=true&localWorkDirectory=%s",
                service.address(), service.shareName(), service.userName(), service.password(), testDirectory.toAbsolutePath());
    }

    @Test
    public void testRenameReadLock() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello World");

        mock.assertIsSatisfied();

        // give test some time to close file resources
        // now the lwd file should be deleted
        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> assertFileNotExists(testDirectory.resolve("hello.txt")));

        assertFileExists(testDirectory.resolve("out/hello.txt"), "Hello World");
    }

    private void prepareSmbServer() throws Exception {
        template.sendBodyAndHeader(getSmbUrl(), "Hello World", Exchange.FILE_NAME, "hello.txt");

        // prepares the SMB Server by creating a file on the server that we want
        // to unit test that we can pool
        Endpoint endpoint = context.getEndpoint(getSmbUrl());
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
                from(getSmbUrl())
                        .to("mock:result", TestSupport.fileUri(testDirectory, "out"));
            }
        };
    }
}
