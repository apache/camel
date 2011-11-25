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
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test based on end user problem with SFTP on Windows
 */
public class FromFtpMoveFileAbsoluteFolderRecursiveTest extends FtpServerTestSupport {

    protected String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/movefile?password=admin&recursive=true&binary=false"
                + "&move=/.done/${file:name}.old&initialDelay=2500&delay=5000";
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        prepareFtpServer();
    }

    @Test
    public void testPollFileAndShouldBeMoved() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("Hello", "Bye", "Goodday");
        mock.expectedFileExists(FTP_ROOT_DIR + "/.done/hello.txt.old");
        mock.expectedFileExists(FTP_ROOT_DIR + "/.done/bye/bye.txt.old");
        mock.expectedFileExists(FTP_ROOT_DIR + "/.done/goodday/goodday.txt.old");

        mock.assertIsSatisfied();
    }
    
    private void prepareFtpServer() throws Exception {
        template.sendBodyAndHeader(getFtpUrl(), "Hello", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader(getFtpUrl(), "Bye", Exchange.FILE_NAME, "bye/bye.txt");
        template.sendBodyAndHeader(getFtpUrl(), "Goodday", Exchange.FILE_NAME, "goodday/goodday.txt");
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(getFtpUrl()).to("mock:result");
            }
        };
    }
}