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
package org.apache.camel.component.file;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for the file sort by expression
 */
public class FileSortByExpressionTest extends ContextTestSupport {

    private String fileUrl = "file://target/data/filesorter/";

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/filesorter");
        super.setUp();
    }

    private void prepareFolder(String folder) {
        template.sendBodyAndHeader("file:target/data/filesorter/" + folder, "Hello Paris", Exchange.FILE_NAME, "paris.dat");

        template.sendBodyAndHeader("file:target/data/filesorter/" + folder, "Hello London", Exchange.FILE_NAME, "london.txt");

        template.sendBodyAndHeader("file:target/data/filesorter/" + folder, "Hello Copenhagen", Exchange.FILE_NAME, "copenhagen.xml");
    }

    @Test
    public void testSortFiles() throws Exception {
        prepareFolder("a");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUrl + "a/?initialDelay=0&delay=10&sortBy=file:ext").to("mock:result");
            }
        });

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello Paris", "Hello London", "Hello Copenhagen");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSortFilesReverse() throws Exception {
        prepareFolder("b");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUrl + "b/?initialDelay=0&delay=10&sortBy=reverse:file:ext").convertBodyTo(String.class).to("mock:reverse");
            }
        });

        MockEndpoint reverse = getMockEndpoint("mock:reverse");
        reverse.expectedBodiesReceived("Hello Copenhagen", "Hello London", "Hello Paris");

        assertMockEndpointsSatisfied();
    }

}
