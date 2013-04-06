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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * @version 
 */
public class FtpReconnectAttemptServerStoppedTest extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/reconnect?password=admin&maximumReconnectAttempts=2&reconnectDelay=500&delete=true";
    }

    @Test
    public void testFromFileToFtp() throws Exception {
        // suspect serve so we cannot connect
        ftpServer.suspend();

        // put a file in the folder (do not use ftp as we then will connect)
        template.sendBodyAndHeader("file:" + FTP_ROOT_DIR + "/reconnect", "Hello World", Exchange.FILE_NAME, "hello.txt");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        // let it run a little
        Thread.sleep(3000);

        assertMockEndpointsSatisfied();

        mock.reset();
        mock.expectedMessageCount(1);

        // resume the server so we can connect
        ftpServer.resume();

        // wait a bit so that the server resumes properly
        Thread.sleep(3000);

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(getFtpUrl()).to("mock:result");
            }
        };
    }
}