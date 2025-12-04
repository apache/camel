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

import java.util.UUID;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StringHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verify the standard file url paths on windows that are interpreted as the window's url paths without the volume name
 * will work on windows system.
 */
public class FileRouteOnDosWithNoVolTest extends ContextTestSupport {
    private static final String TEST_FILE_NAME = "hello" + UUID.randomUUID() + ".txt";

    private String path;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        path = testDirectory("dosnovol").toAbsolutePath().toString();
        if (FileUtil.isWindows()) {
            path = StringHelper.after(path, ":\\", path).replace('\\', '/');
        }

        super.setUp();
    }

    @Test
    public void testRouteFileToFile() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedFileExists(path + "/route/out/" + TEST_FILE_NAME);

        template.sendBodyAndHeader(
                "file://" + path + "/route/poller", "Hello World", Exchange.FILE_NAME, TEST_FILE_NAME);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRouteFromFileOnly() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader(
                "file://" + path + "/from/poller", "Hello World", Exchange.FILE_NAME, TEST_FILE_NAME);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRouteToFileOnly() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedFileExists(path + "/to/out/" + TEST_FILE_NAME);

        template.sendBodyAndHeader("direct:report", "Hello World", Exchange.FILE_NAME, TEST_FILE_NAME);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("file://" + path + "/route/poller?initialDelay=0&delay=10")
                        .to("file://" + path + "/route/out", "mock:result");
                from("file://" + path + "/from/poller?initialDelay=0&delay=10").to("mock:result");
                from("direct:report").to("file://" + path + "/to/out", "mock:result");
            }
        };
    }
}
