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

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 *
 */
public class FileConsumeAlterFileNameHeaderIssueTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/files");
        super.setUp();
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testConsumeAndDeleteRemoveAllHeaders() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/files?initialDelay=0&delay=10&delete=true")
                    // remove all headers
                    .removeHeaders("*")
                    .to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("file://target/files", "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();
        oneExchangeDone.matchesMockWaitTime();

        assertFalse("Headers should have been removed", mock.getExchanges().get(0).getIn().hasHeaders());

        // the original file should have been deleted, as the file consumer should be resilient against
        // end users deleting headers
        assertFalse("File should been deleted", new File("target/files/hello.txt").exists());
    }

    public void testConsumeAndDeleteChangeFileHeader() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/files?initialDelay=0&delay=10&delete=true")
                    // change file header
                    .setHeader(Exchange.FILE_NAME, constant("bye.txt"))
                    .to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived(Exchange.FILE_NAME, "bye.txt");

        template.sendBodyAndHeader("file://target/files", "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();
        oneExchangeDone.matchesMockWaitTime();

        // the original file should have been deleted, as the file consumer should be resilient against
        // end users changing headers
        assertFalse("File should been deleted", new File("target/files/hello.txt").exists());
    }

    public void testConsumeAndMoveRemoveAllHeaders() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/files?initialDelay=0&delay=10")
                    // remove all headers
                    .removeHeaders("*")
                    .to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("file://target/files", "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();
        oneExchangeDone.matchesMockWaitTime();

        assertFalse("Headers should have been removed", mock.getExchanges().get(0).getIn().hasHeaders());

        // the original file should have been moved, as the file consumer should be resilient against
        // end users deleting headers
        assertTrue("File should been moved", new File("target/files/.camel/hello.txt").exists());
    }

    public void testConsumeAndMoveChangeFileHeader() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/files?initialDelay=0&delay=10")
                    // change file header
                    .setHeader(Exchange.FILE_NAME, constant("bye.txt"))
                    .to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived(Exchange.FILE_NAME, "bye.txt");

        template.sendBodyAndHeader("file://target/files", "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();
        oneExchangeDone.matchesMockWaitTime();

        // the original file should have been moved, as the file consumer should be resilient against
        // end users changing headers
        assertTrue("File should been moved", new File("target/files/.camel/hello.txt").exists());
    }

}
