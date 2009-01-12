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
import org.apache.camel.impl.JndiRegistry;

/**
 * Unit test to test filter option.
 */
public class FromFtpFilterTest extends FtpServerTestSupport {

    private int port = 20077;
    private String ftpUrl = "ftp://admin@localhost:" + port + "/filter?password=admin&binary=false&filter=#myFilter";

    public int getPort() {
        return port;
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myFilter", new MyFileFilter());
        return jndi;
    }

    public void testFilterFiles() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        template.sendBodyAndHeader(ftpUrl, "This is a file to be filtered",
                FileComponent.HEADER_FILE_NAME, "skipme.txt");

        mock.setResultWaitTime(3000);
        mock.assertIsSatisfied();
    }

    public void testFilterFilesWithARegularFile() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader(ftpUrl, "This is a file to be filtered",
                FileComponent.HEADER_FILE_NAME, "skipme.txt");

        template.sendBodyAndHeader(ftpUrl, "Hello World",
                FileComponent.HEADER_FILE_NAME, "hello.txt");

        mock.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(ftpUrl).to("mock:result");
            }
        };
    }

    // START SNIPPET: e1
    public class MyFileFilter implements RemoteFileFilter {
        public boolean accept(RemoteFile file) {
            // we dont accept any files starting with skip in the name
            return !file.getFileName().startsWith("skip");
        }
    }
    // END SNIPPET: e1
}