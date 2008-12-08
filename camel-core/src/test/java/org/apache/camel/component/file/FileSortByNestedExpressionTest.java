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
 * Unit test for  the file sort by expression with nested groups
 */
public class FileSortByNestedExpressionTest extends ContextTestSupport {

    private String fileUrl = "file://target/filesorter/";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        deleteDirectory("target/filesorter");
    }

    private void prepareFolder(String folder) {
        template.sendBodyAndHeader("file:target/filesorter/" + folder, "Hello Paris",
            FileComponent.HEADER_FILE_NAME, "paris.txt");

        template.sendBodyAndHeader("file:target/filesorter/" + folder, "Hello London",
            FileComponent.HEADER_FILE_NAME, "london.txt");

        template.sendBodyAndHeader("file:target/filesorter/" + folder, "Hello Copenhagen",
            FileComponent.HEADER_FILE_NAME, "copenhagen.xml");

        template.sendBodyAndHeader("file:target/filesorter/" + folder, "Hello Dublin",
            FileComponent.HEADER_FILE_NAME, "dublin.txt");
    }

    public void testSortNestedFiles() throws Exception {
        prepareFolder("a");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello Dublin", "Hello London", "Hello Paris", "Hello Copenhagen");

        assertMockEndpointsSatisfied();
    }

    public void testSortNestedFilesReverse() throws Exception {
        prepareFolder("b");
        MockEndpoint reverse = getMockEndpoint("mock:reverse");
        reverse.expectedBodiesReceived("Hello Paris", "Hello London", "Hello Dublin", "Hello Copenhagen");

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(fileUrl + "a/?sortBy=file:name.ext;file:name").to("mock:result");

                from(fileUrl + "b/?sortBy=file:name.ext;reverse:file:name").to("mock:reverse");
            }
        };
    }

}