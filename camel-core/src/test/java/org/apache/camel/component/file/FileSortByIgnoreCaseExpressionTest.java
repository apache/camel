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
 * Unit test for  the file sort by expression
 */
public class FileSortByIgnoreCaseExpressionTest extends ContextTestSupport {

    private String fileUrl = "file://target/filesorter/";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        deleteDirectory("target/filesorter");
    }

    private void prepareFolder(String folder) {
        template.sendBodyAndHeader("file:target/filesorter/" + folder, "Hello Paris",
            FileComponent.HEADER_FILE_NAME, "report-3.dat");

        template.sendBodyAndHeader("file:target/filesorter/" + folder, "Hello London",
            FileComponent.HEADER_FILE_NAME, "REPORT-2.txt");

        template.sendBodyAndHeader("file:target/filesorter/" + folder, "Hello Copenhagen",
            FileComponent.HEADER_FILE_NAME, "Report-1.xml");
    }

    public void testSortFiles() throws Exception {
        prepareFolder("a");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello London", "Hello Copenhagen", "Hello Paris");

        assertMockEndpointsSatisfied();
    }

    public void testSortFilesNoCase() throws Exception {
        prepareFolder("b");
        MockEndpoint nocase = getMockEndpoint("mock:nocase");
        nocase.expectedBodiesReceived("Hello Copenhagen", "Hello London", "Hello Paris");

        assertMockEndpointsSatisfied();
    }

    public void testSortFilesNoCaseReverse() throws Exception {
        prepareFolder("c");
        MockEndpoint nocasereverse = getMockEndpoint("mock:nocasereverse");
        nocasereverse.expectedBodiesReceived("Hello Paris", "Hello London", "Hello Copenhagen");

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(fileUrl + "a/?sortBy=file:name").to("mock:result");

                from(fileUrl + "b/?sortBy=ignoreCase:file:name").to("mock:nocase");

                from(fileUrl + "c/?sortBy=reverse:ignoreCase:file:name").to("mock:nocasereverse");
            }
        };
    }

}