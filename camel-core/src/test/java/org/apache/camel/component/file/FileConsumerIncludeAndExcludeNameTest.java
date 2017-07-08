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
 * Unit test that file consumer will include/exclude pre and postfixes
 */
public class FileConsumerIncludeAndExcludeNameTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/includeexclude");
        super.setUp();
    }

    public void testIncludePreAndPostfixes() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("Report 2", "Report 3", "Report 4");
        mock.expectedMessageCount(3);

        sendFiles();

        mock.assertIsSatisfied();
    }

    private void sendFiles() throws Exception {
        String url = "file://target/includeexclude";
        template.sendBodyAndHeader(url, "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader(url, "Report 1", Exchange.FILE_NAME, "report1.xml");
        template.sendBodyAndHeader(url, "Report 2", Exchange.FILE_NAME, "report2.txt");
        template.sendBodyAndHeader(url, "Report 3", Exchange.FILE_NAME, "report3.txt");
        template.sendBodyAndHeader(url, "Report 4", Exchange.FILE_NAME, "Report4.txt");
        template.sendBodyAndHeader(url, "Secret", Exchange.FILE_NAME, "Secret.txt");
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("file://target/includeexclude/?initialDelay=0&delay=10&include=report.*txt&exclude=hello.*")
                    .convertBodyTo(String.class).to("mock:result");
            }
        };
    }

}