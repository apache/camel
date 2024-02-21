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
import java.io.InputStream;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;

// README (informative only, to be removed in final version):
// The filesize at which this test fails is arbitrary, I suppose it depends on the FTP we are using and Java
// caching policy (my assumptions, not proved yet). To trigger the error the file must not be cached, this way the
// server stays in the downloading state with 150 reply sent but before sending 226 Download complete.
// On plain local vsftpd the download failed for every > 1mb file, here I had to make it bigger to trigger the error.

public class FtpSimpleConsumeStreamingStepwiseIT extends FtpServerTestSupport {
    @TempDir
    Path testDirectory;

    private String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}/tmp4/camel?password=admin&binary=true&delay=5000" +
               "&stepwise=false&streamDownload=true";
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
        resultEndpoint.expectedMessageCount(1);

        resultEndpoint.assertIsSatisfied();

        InputStream is = resultEndpoint.getExchanges().get(0).getIn().getBody(InputStream.class);
        assertNotNull(is);
    }

    private void prepareFtpServer() throws Exception {
        // prepares the FTP Server by putting a file on the server
        Endpoint endpoint = context.getEndpoint(getFtpUrl());
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody(new File("src/test/data/ftpbinarytest/logo3.jpeg"));
        exchange.getIn().setHeader(Exchange.FILE_NAME, "logo3.jpeg");
        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);
        producer.stop();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(getFtpUrl()).setHeader(Exchange.FILE_NAME, constant("deleteme.jpg"))
                        .to(TestSupport.fileUri(testDirectory), "mock:result");
            }
        };
    }
}
