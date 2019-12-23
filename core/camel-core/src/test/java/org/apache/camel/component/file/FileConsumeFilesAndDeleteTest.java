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

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for consuming the same filename only.
 */
public class FileConsumeFilesAndDeleteTest extends ContextTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/files");
        super.setUp();
    }

    @Test
    public void testConsumeAndDelete() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("file://target/data/files", "Bye World", Exchange.FILE_NAME, "report2.txt");
        template.sendBodyAndHeader("file://target/data/files", "Hello World", Exchange.FILE_NAME, "report.txt");
        template.sendBodyAndHeader("file://target/data/files/2008", "2008 Report", Exchange.FILE_NAME, "report2008.txt");

        assertMockEndpointsSatisfied();

        oneExchangeDone.matchesMockWaitTime();

        // file should not exists
        assertFalse("File should been deleted", new File("target/data/files/report.txt").exists());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("file://target/data/files/?initialDelay=0&delay=10&fileName=report.txt&delete=true").convertBodyTo(String.class).to("mock:result");
            }
        };
    }
}
