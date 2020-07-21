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
package org.apache.camel.component.file.remote.sftp;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FromSftpRecursiveNotStepwiseNoBasePathTest extends SftpServerTestSupport {

    protected String getSftpUrl() {
        return "sftp://admin@localhost:" + getPort() + "?password=admin&initialDelay=3000&stepwise=false" + "&recursive=true";
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        rootDirMode = true;
        super.setUp();
        prepareFtpServer();
    }

    @Test
    public void testRecursiveNotStepwiseNoBasePath() throws Exception {
        // CAMEL-13400
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("Bye World", "Hello World", "Goodday World");
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(getSftpUrl()).convertBodyTo(String.class).to("mock:result");
            }
        };
    }

    private void prepareFtpServer() throws Exception {
        sendFile("Bye World", "bye.txt");
        sendFile("Hello World", "sub/hello.txt");
        sendFile("Goodday World", "sub/sub2/godday.txt");
    }

    public void sendFile(Object body, String fileName) {
        template.sendBodyAndHeader("file://" + FTP_ROOT_DIR, body, Exchange.FILE_NAME, fileName);
    }
}
