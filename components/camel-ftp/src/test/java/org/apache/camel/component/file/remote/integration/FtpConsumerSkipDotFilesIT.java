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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

/**
 * Unit test that ftp consumer will skip any files starting with a dot
 */
public class FtpConsumerSkipDotFilesIT extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}/dotfiles?password=admin&noop=true";
    }

    private String getFtpProducerUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}/dotfiles?password=admin";
    }

    @Override
    public void doPostSetup() throws Exception {
        prepareFtpServer();
    }

    @Test
    public void testSkipDotFiles() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.expectedBodiesReceived("Reports", "Reports");
        mock.assertIsSatisfied();
    }

    private void prepareFtpServer() {
        // prepares the FTP Server by creating files on the server that we want
        // to unit
        // test that we can pool and store as a local file
        sendFile(getFtpProducerUrl(), "Hello World", ".skipme");
        sendFile(getFtpProducerUrl(), "Reports", "report1.txt");
        sendFile(getFtpProducerUrl(), "Bye World", ".camel");
        sendFile(getFtpProducerUrl(), "Reports", "report2.txt");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(getFtpUrl()).to("mock:result");
            }
        };
    }
}
