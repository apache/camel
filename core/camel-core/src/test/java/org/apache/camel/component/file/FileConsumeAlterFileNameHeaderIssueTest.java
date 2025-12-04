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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.util.UUID;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

/**
 *
 */
public class FileConsumeAlterFileNameHeaderIssueTest extends ContextTestSupport {
    public static final String TEST_FILE_NAME = "hello." + UUID.randomUUID() + ".txt";
    public static final String TEST_FILE_NAME_BYE = "bye." + UUID.randomUUID() + ".txt";

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testConsumeAndDeleteRemoveAllHeaders() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri() + "?initialDelay=0&delay=10&delete=true")
                        // remove all headers
                        .removeHeaders("*")
                        .to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, TEST_FILE_NAME);

        assertMockEndpointsSatisfied();
        oneExchangeDone.matchesWaitTime();

        assertFalse(mock.getExchanges().get(0).getIn().hasHeaders(), "Headers should have been removed");

        // the original file should have been deleted, as the file consumer
        // should be resilient against
        // end users deleting headers
        assertFalse(Files.exists(testFile(TEST_FILE_NAME)), "File should been deleted");
    }

    @Test
    public void testConsumeAndDeleteChangeFileHeader() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri() + "?initialDelay=0&delay=10&delete=true")
                        // change file header
                        .setHeader(Exchange.FILE_NAME, constant(TEST_FILE_NAME_BYE))
                        .to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived(Exchange.FILE_NAME, TEST_FILE_NAME_BYE);

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, TEST_FILE_NAME);

        assertMockEndpointsSatisfied();
        oneExchangeDone.matchesWaitTime();

        // the original file should have been deleted, as the file consumer
        // should be resilient against
        // end users changing headers
        assertFalse(Files.exists(testFile(TEST_FILE_NAME)), "File should been deleted");
    }

    @Test
    public void testConsumeAndMoveRemoveAllHeaders() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri() + "?initialDelay=0&delay=10")
                        // remove all headers
                        .removeHeaders("*")
                        .to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, TEST_FILE_NAME);

        assertMockEndpointsSatisfied();
        oneExchangeDone.matchesWaitTime();

        assertFalse(mock.getExchanges().get(0).getIn().hasHeaders(), "Headers should have been removed");

        // the original file should have been moved, as the file consumer should
        // be resilient against
        // end users deleting headers
        assertTrue(Files.exists(testFile(".camel/" + TEST_FILE_NAME)), "File should been moved");
    }

    @Test
    public void testConsumeAndMoveChangeFileHeader() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri() + "?initialDelay=0&delay=10")
                        // change file header
                        .setHeader(Exchange.FILE_NAME, constant(TEST_FILE_NAME_BYE))
                        .to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived(Exchange.FILE_NAME, TEST_FILE_NAME_BYE);

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, TEST_FILE_NAME);

        assertMockEndpointsSatisfied();
        oneExchangeDone.matchesWaitTime();

        // the original file should have been moved, as the file consumer should
        // be resilient against
        // end users changing headers
        assertTrue(Files.exists(testFile(".camel/" + TEST_FILE_NAME)), "File should been moved");
    }
}
