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

    private String fileUrl = "newfile://target/filesorter/";

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/filesorter");
        super.setUp();
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private void prepareFolder(String folder) {
        template.sendBodyAndHeader("newfile:target/filesorter/" + folder, "Hello Paris",
            NewFileComponent.HEADER_FILE_NAME, "report-3.dat");

        template.sendBodyAndHeader("newfile:target/filesorter/" + folder, "Hello London",
            NewFileComponent.HEADER_FILE_NAME, "REPORT-2.txt");

        template.sendBodyAndHeader("newfile:target/filesorter/" + folder, "Hello Copenhagen",
            NewFileComponent.HEADER_FILE_NAME, "Report-1.xml");
    }

    public void testSortFilesByNameWithCase() throws Exception {
        prepareFolder("a");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUrl + "a/?sortBy=file:name&initialDelay=250&delay=1000").to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello London", "Hello Copenhagen", "Hello Paris");

        assertMockEndpointsSatisfied();
    }

    public void testSortFilesByNameNoCase() throws Exception {
        prepareFolder("b");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUrl + "b/?sortBy=ignoreCase:file:name&initialDelay=500&delay=1000").to("mock:nocase");
            }
        });
        context.start();

        MockEndpoint nocase = getMockEndpoint("mock:nocase");
        nocase.expectedBodiesReceived("Hello Copenhagen", "Hello London", "Hello Paris");

        assertMockEndpointsSatisfied();
    }

    public void testSortFilesByNameNoCaseReverse() throws Exception {
        prepareFolder("c");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUrl + "c/?sortBy=reverse:ignoreCase:file:name&initialDelay=750&delay=1000").to("mock:nocasereverse");
            }
        });
        context.start();

        MockEndpoint nocasereverse = getMockEndpoint("mock:nocasereverse");
        nocasereverse.expectedBodiesReceived("Hello Paris", "Hello London", "Hello Copenhagen");

        assertMockEndpointsSatisfied();
    }

}