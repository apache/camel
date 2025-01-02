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
package org.apache.camel.component.smb2;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class SmbConsumerExcludeExtIT extends SmbServerTestSupport {

    private String getSbmUrl() {
        return String.format(
                "smb2:%s/%s?username=%s&password=%s&path=/excludeext&excludeExt=xml",
                service.address(), service.shareName(), service.userName(), service.password());
    }

    @Override
    public void doPostSetup() throws Exception {
        prepareSmbServer();
    }

    @Test
    public void testExcludeExt() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("Report 1", "Bye World");

        mock.assertIsSatisfied();
    }

    private void prepareSmbServer() {
        // prepares the SMB Server by creating files on the server that we want to unit
        // test that we can pool and store as a local file
        sendFile(getSbmUrl(), "Hello World", "hello.xml");
        sendFile(getSbmUrl(), "Report 1", "report1.txt");
        sendFile(getSbmUrl(), "Bye World", "secret.DAT");
        sendFile(getSbmUrl(), "Report 2", "report2.xml");
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
