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
package org.apache.camel.component.smb;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class SmbConsumerDirectoriesNotMatchedIT extends SmbServerTestSupport {

    private String getSbmUrl() {
        return String.format(
                "smb:%s/%s?username=%s&password=%s&path=/dirnotmatched&recursive=true&delete=true&include=^.*txt$",
                service.address(), service.shareName(), service.userName(), service.password());
    }

    @Override
    public void doPostSetup() throws Exception {
        prepareSmbServer();
    }

    @Test
    public void testSkipDirectories() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(3);
        mock.assertIsSatisfied();
    }

    private void prepareSmbServer() {
        // prepares the SMB Server by creating files on the server that we want
        // to unit test that we can pool and store as a local file

        sendFile(getSbmUrl() + "/?password=admin", "This is a dot file", ".skipme");
        sendFile(getSbmUrl() + "/?password=admin", "This is a web file", "index.html");
        sendFile(getSbmUrl() + "/?password=admin", "This is a readme file", "readme.txt");
        sendFile(getSbmUrl() + "/2007/?password=admin", "2007 report", "report2007.txt");
        sendFile(getSbmUrl() + "/2008/?password=admin", "2008 report", "report2008.txt");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(getSbmUrl()).to("mock:result");
            }
        };
    }
}
