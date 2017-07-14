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
package org.apache.camel.component.file;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test for empty files
 */
public class FileConsumerInterceptEmptyFileTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/exclude");
        super.setUp();
    }

    public void testExcludeZeroLengthFiles() throws Exception {
        MockEndpoint mock1 = getMockEndpoint("mock:result");
        mock1.expectedBodiesReceivedInAnyOrder("Hello World", "Bye World");

        MockEndpoint mock2 = getMockEndpoint("mock:skip");
        mock2.expectedMessageCount(2);
        
        sendFiles();

        assertMockEndpointsSatisfied();
    }

    private void sendFiles() throws Exception {
        String url = "file://target/exclude";
        template.sendBodyAndHeader(url, "Hello World", Exchange.FILE_NAME, "hello.xml");
        template.sendBodyAndHeader(url, "", Exchange.FILE_NAME, "empty1.txt");
        template.sendBodyAndHeader(url, "Bye World", Exchange.FILE_NAME, "secret.txt");
        template.sendBodyAndHeader(url, "", Exchange.FILE_NAME, "empty2.txt");
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                interceptFrom().when(simple("${file:length} == 0")).to("mock:skip").stop();

                from("file://target/exclude/?initialDelay=10&delay=10")
                    .convertBodyTo(String.class).to("log:test").to("mock:result");
            }
        };
    }

}
