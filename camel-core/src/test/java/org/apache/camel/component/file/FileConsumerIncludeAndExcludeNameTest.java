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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test that file consumer will include/exclude pre and postfixes
 */
public class FileConsumerIncludeAndExcludeNameTest extends ContextTestSupport {

    public void testIncludePreAndPostfixes() throws Exception {
        deleteDirectory("./target/includeexclude");
        prepareFiles();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.expectedBodiesReceivedInAnyOrder("Report 2", "Report 3");
        mock.assertIsSatisfied();
    }

    private void prepareFiles() throws Exception {
        String url = "newfile://target/includeexclude";
        template.sendBodyAndHeader(url, "Hello World", NewFileComponent.HEADER_FILE_NAME, "hello.xml");
        template.sendBodyAndHeader(url, "Report 1", NewFileComponent.HEADER_FILE_NAME, "report1.xml");
        template.sendBodyAndHeader(url, "Report 2", NewFileComponent.HEADER_FILE_NAME, "report2.txt");
        template.sendBodyAndHeader(url, "Report 3", NewFileComponent.HEADER_FILE_NAME, "report3.txt");
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("newfile://target/includeexclude/?includeNamePrefix=report&excludeNamePostfix=xml")
                    .to("mock:result");
            }
        };
    }

}