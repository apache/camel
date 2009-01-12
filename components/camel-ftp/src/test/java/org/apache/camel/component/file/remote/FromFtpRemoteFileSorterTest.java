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

import java.util.Comparator;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;

/**
 * Unit test to verify remotefile sorter option.
 */
public class FromFtpRemoteFileSorterTest extends FtpServerTestSupport {

    private int port = 20095;

    private String ftpUrl = "ftp://admin@localhost:" + port + "/sorter?password=admin&sorter=#mySorter";

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("mySorter", new MyRemoteFileSorter());
        return jndi;
    }

    public void testFtpSorter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(3);
        mock.expectedBodiesReceived("Hello Copenhagen", "Hello London", "Hello Paris");
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
        // test that we can pool
        String ftpUrl = "ftp://admin@localhost:" + port + "/sorter/?password=admin";
        template.sendBodyAndHeader(ftpUrl, "Hello Paris", FileComponent.HEADER_FILE_NAME, "paris.txt");
        template.sendBodyAndHeader(ftpUrl, "Hello London", FileComponent.HEADER_FILE_NAME, "london.txt");
        template.sendBodyAndHeader(ftpUrl, "Hello Copenhagen", FileComponent.HEADER_FILE_NAME, "copenhagen.txt");
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(ftpUrl).to("mock:result");
            }
        };
    }

    // START SNIPPET: e1
    public class MyRemoteFileSorter implements Comparator<RemoteFile> {

        public int compare(RemoteFile o1, RemoteFile o2) {
            return o1.getFileName().compareTo(o2.getFileName());
        }
    }
    // END SNIPPET: e1


}