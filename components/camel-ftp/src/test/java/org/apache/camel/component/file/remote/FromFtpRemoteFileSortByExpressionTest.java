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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test to verify remotefile sortby option.
 */
public class FromFtpRemoteFileSortByExpressionTest extends FtpServerTestSupport {

    private int port = 20094;

    private String ftpUrl = "ftp://admin@localhost:" + port + "/sortby?password=admin&consumer.delay=5000";

    public int getPort() {
        return port;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        prepareFtpServer();
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testSortFiles() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(ftpUrl + "&sortBy=file:name.ext").to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello Paris", "Hello London", "Hello Copenhagen");

        assertMockEndpointsSatisfied();
    }

    public void testSortFilesReverse() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(ftpUrl + "&sortBy=reverse:file:name.ext").to("mock:reverse");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:reverse");
        mock.expectedBodiesReceived("Hello Copenhagen", "Hello London", "Hello Paris");

        assertMockEndpointsSatisfied();
    }

    private void prepareFtpServer() throws Exception {
        // prepares the FTP Server by creating files on the server that we want to unit
        // test that we can pool
        String ftpUrl = "ftp://admin@localhost:" + port + "/sortby/?password=admin";
        template.sendBodyAndHeader(ftpUrl, "Hello Paris", FileComponent.HEADER_FILE_NAME, "paris.dat");
        template.sendBodyAndHeader(ftpUrl, "Hello London", FileComponent.HEADER_FILE_NAME, "london.txt");
        template.sendBodyAndHeader(ftpUrl, "Hello Copenhagen", FileComponent.HEADER_FILE_NAME, "copenhagen.xml");
    }

}