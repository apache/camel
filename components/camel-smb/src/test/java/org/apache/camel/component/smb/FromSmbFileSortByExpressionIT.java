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

public class FromSmbFileSortByExpressionIT extends SmbServerTestSupport {

    protected String getSmbUrl() {
        return String.format(
                "smb:%s/%s?username=%s&password=%s&path=/sortby&sortBy=file:ext",
                service.address(), service.shareName(), service.userName(), service.password());
    }

    @Override
    public void doPostSetup() throws Exception {
        prepareSmbServer();
    }

    @Test
    public void testSortFiles() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(getSmbUrl()).to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello Paris", "Hello London", "Hello Copenhagen");

        MockEndpoint.assertIsSatisfied(context);
    }

    private void prepareSmbServer() {
        // prepares the SMB Server by creating files on the server that we want
        // to unit test that we can pool
        sendFile(getSmbUrl(), "Hello Paris", "paris.dat");
        sendFile(getSmbUrl(), "Hello London", "london.txt");
        sendFile(getSmbUrl(), "Hello Copenhagen", "copenhagen.xml");
    }
}
