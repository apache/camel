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

import java.nio.file.Files;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 *
 */
public class FileConsumeCharsetTest extends ContextTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        template.sendBodyAndHeader(fileUri("?charset=UTF-8"), "Hello World \u4f60\u597d", Exchange.FILE_NAME,
                "report.txt");
    }

    @Test
    public void testConsumeAndDelete() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World \u4f60\u597d");

        assertMockEndpointsSatisfied();

        oneExchangeDone.matchesWaitTime();

        // file should not exists
        assertFalse(Files.exists(testFile("report.txt")), "File should been deleted");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(fileUri("?initialDelay=0&delay=10&fileName=report.txt&delete=true&charset=UTF-8"))
                        .convertBodyTo(String.class).to("mock:result");
            }
        };
    }

}
