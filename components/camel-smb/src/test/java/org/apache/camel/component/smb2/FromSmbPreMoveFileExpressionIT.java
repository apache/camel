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
package org.apache.camel.component.smb2;

import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FromSmbPreMoveFileExpressionIT extends SmbServerTestSupport {

    protected String getSmbUrl() {
        return String.format(
                "smb2:%s/%s?username=%s&password=%s&path=/premoveexpr&delay=5000&preMove=../inprogress/${file:name.noext}.bak",
                service.address(), service.shareName(), service.userName(), service.password());
    }

    @Override
    public void doPostSetup() throws Exception {
        prepareSmbServer();
    }

    @Test
    public void testPollFileAndShouldBeMoved() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello World this file will be moved");

        mock.assertIsSatisfied();
    }

    private void prepareSmbServer() throws Exception {
        // prepares the SMB Server by creating a file on the server that we want
        // to unit test that we can pool and store as a local file
        Endpoint endpoint = context.getEndpoint(getSmbUrl());
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello World this file will be moved");
        exchange.getIn().setHeader(Exchange.FILE_NAME, "hello.txt");
        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);
        producer.stop();

        // assert file is created
        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals("Hello World this file will be moved",
                        new String(copyFileContentFromContainer("/data/rw/premoveexpr/hello.txt"))));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(getSmbUrl()).process(new Processor() {
                    public void process(Exchange exchange) {
                        // assert the file is pre moved
                        await().atMost(3, TimeUnit.SECONDS)
                                .untilAsserted(() -> assertEquals("Hello World this file will be moved",
                                        new String(copyFileContentFromContainer("/data/rw/inprogress/hello.bak"))));
                    }
                }).to("mock:result");
            }
        };
    }
}
