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
 * Unit test CAMEL-1247
 *
 * @version $Revision$
 */
public class FtpConsumerWithNoFileOptionTest extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "?password=admin&consumer.delay=5000";
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        deleteDirectory(FTP_ROOT_DIR);
        createDirectory(FTP_ROOT_DIR);
        prepareFtpServer();
    }

    private void prepareFtpServer() throws Exception {
        template.sendBodyAndHeader(getFtpUrl(), "Hello World", FileComponent.HEADER_FILE_NAME, "hello.txt");
    }

    public void testWithNoFileInOption() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        assertMockEndpointsSatisfied();

        RemoteFileExchange exchange = (RemoteFileExchange) mock.getExchanges().get(0);
        RemoteFile file = (RemoteFile) exchange.getGenericFile();
        assertEquals("hello.txt", file.getAbsoluteFileName());
        assertEquals("hello.txt", file.getRelativeFileName());
        assertEquals("hello.txt", file.getFileName());
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(getFtpUrl()).to("mock:result");
            }
        };
    }

}