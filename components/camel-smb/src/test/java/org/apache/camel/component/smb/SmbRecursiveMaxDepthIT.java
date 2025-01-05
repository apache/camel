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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class SmbRecursiveMaxDepthIT extends SmbServerTestSupport {

    @Override
    public void doPostSetup() throws Exception {
        prepareSmbServer();
    }

    protected String getSmbUrl() {
        return String.format(
                "smb:%s/%s?username=%s&password=%s&path=/uploadmax&recursive=true&maxdepth=3&initialDelay=3000",
                service.address(), service.shareName(), service.userName(), service.password());
    }

    @Test
    public void testDirectoryTraversalDepth() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:received_send");
        mock.expectedBodiesReceived("Goodday", "World");

        mock.assertIsSatisfied();
    }

    private void prepareSmbServer() throws Exception {
        template.sendBodyAndHeader(getSmbUrl(), "Goodday", Exchange.FILE_NAME, "user/greet/goodday.txt");
        template.sendBodyAndHeader(getSmbUrl(), "Hello", Exchange.FILE_NAME, "user/greet/en/hello.txt");
        template.sendBodyAndHeader(getSmbUrl(), "World", Exchange.FILE_NAME, "user/world.txt");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(getSmbUrl())
                        .to("mock:received_send");
            }
        };
    }
}
