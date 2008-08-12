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
 * Unit test that ftp consumer will exclude pre and postfixes
 */
public class FtpConsumerExcludeNameTest extends FtpServerTestSupport {

    private int port = 20095;

    private String ftpUrl = "ftp://admin@localhost:" + port + "/excludename?password=admin"
        + "&consumer.excludedNamePrefix=secret&consumer.excludedNamePostfix=xml";

    public void testExludePreAndPostfixes() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.expectedBodiesReceived("Reports", "Reports");
        mock.assertIsSatisfied();
    }

    public int getPort() {
        return port;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        prepareFtpServer();
    }

    private void prepareFtpServer() throws Exception {
        // prepares the FTP Server by creating files on the server that we want to unit
        // test that we can pool and store as a local file
        String ftpUrl = "ftp://admin@localhost:" + port + "/excludename/?password=admin";
        template.sendBodyAndHeader(ftpUrl, "Hello World", FileComponent.HEADER_FILE_NAME, "hello.xml");
        template.sendBodyAndHeader(ftpUrl, "Reports", FileComponent.HEADER_FILE_NAME, "report1.txt");
        template.sendBodyAndHeader(ftpUrl, "Bye World", FileComponent.HEADER_FILE_NAME, "secret.txt");
        template.sendBodyAndHeader(ftpUrl, "Reports", FileComponent.HEADER_FILE_NAME, "report2.txt");
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(ftpUrl).to("mock:result");
            }
        };
    }

}
