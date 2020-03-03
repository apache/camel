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

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit test for ftpClientConfig option.
 */
public class FtpConsumerUsingFTPClientConfigTest extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/clientconfig?password=admin&ftpClientConfig=#myConfig";
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        prepareFtpServer();
    }

    @BindToRegistry("myConfig")
    public FTPClientConfig createConfig() {
        FTPClientConfig config = new FTPClientConfig(FTPClientConfig.SYST_UNIX);
        config.setServerTimeZoneId("Europe/Paris");
        return config;
    }

    @Test
    public void testFTPClientConfig() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello World");

        assertMockEndpointsSatisfied();
    }

    private void prepareFtpServer() throws Exception {
        // prepares the FTP Server by creating files on the server that we want
        // to unit
        // test that we can pool and store as a local file
        sendFile(getFtpUrl(), "Hello World", "hello.txt");
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
