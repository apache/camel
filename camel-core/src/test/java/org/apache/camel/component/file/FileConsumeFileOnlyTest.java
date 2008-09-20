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
 * Unit test for consuming the same filename only.
 */
public class FileConsumeFileOnlyTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        deleteDirectory("target/fileonly");
        template.sendBodyAndHeader("file://target/fileonly", "Hello World", FileComponent.HEADER_FILE_NAME, "report.txt");
        template.sendBodyAndHeader("file://target/fileonly", "Bye World", FileComponent.HEADER_FILE_NAME, "report2.txt");
        template.sendBodyAndHeader("file://target/fileonly/2008", "2008 Report", FileComponent.HEADER_FILE_NAME, "report2008.txt");
    }

    public void testConsumeFileOnly() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("file://target/fileonly/report.txt?consumer.recursive=false&delete=true").to("mock:result");
            }
        };
    }
}
