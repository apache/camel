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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class PollDynamicFileNameTest extends FtpServerTestSupport {

    protected String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}/pollfile?password=admin&binary=false&noop=true";
    }

    @Override
    public void doPostSetup() throws Exception {
        prepareFtpServer();
    }

    @Test
    public void testPollEnrichFileOne() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(2);
        getMockEndpoint("mock:result").message(0).body().isEqualTo("Hello World");
        getMockEndpoint("mock:result").message(1).body().isNull();

        template.sendBodyAndHeader("direct:start", "Foo", "target", "myfile.txt");
        template.sendBodyAndHeader("direct:start", "Bar", "target", "unknown.txt");

        MockEndpoint.assertIsSatisfied(context);

        // there should only be 1 file endpoint
        long c = context.getEndpoints().stream()
                .filter(e -> e.getEndpointKey().startsWith("ftp") && e.getEndpointUri().contains("&fileName=")).count();
        Assertions.assertEquals(1, c, "There should only be 1 ftp endpoint");
    }

    @Test
    public void testPollEnrichFileTwo() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceivedInAnyOrder("Hello World", "Bye World");

        template.sendBodyAndHeader(getFtpUrl(), "Bye World", Exchange.FILE_NAME, "myfile2.txt");

        template.sendBodyAndHeader("direct:start", "Foo", "target", "myfile.txt");
        template.sendBodyAndHeader("direct:start", "Bar", "target", "myfile2.txt");

        MockEndpoint.assertIsSatisfied(context);

        // there should only be 1 file endpoint
        long c = context.getEndpoints().stream()
                .filter(e -> e.getEndpointKey().startsWith("ftp") && e.getEndpointUri().contains("&fileName=")).count();
        Assertions.assertEquals(1, c, "There should only be 1 ftp endpoint");
    }

    private void prepareFtpServer() throws Exception {
        // prepares the FTP Server by creating a file on the server that we want
        // to unit test that we can pool and store as a local file
        Endpoint endpoint = context.getEndpoint(getFtpUrl());

        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello World");
        exchange.getIn().setHeader(Exchange.FILE_NAME, "myfile.txt");

        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);
        producer.stop();

        // assert files is created
        File file = service.ftpFile("pollfile/myfile.txt").toFile();
        assertTrue(file.exists(), "The file should exists");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .poll(getFtpUrl() + "&fileName=${header.target}", 2000)
                        .to("mock:result");
            }
        };
    }
}
