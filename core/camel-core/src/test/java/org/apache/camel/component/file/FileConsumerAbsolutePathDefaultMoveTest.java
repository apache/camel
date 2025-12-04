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
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * Unit test for consuming from an absolute path
 */
public class FileConsumerAbsolutePathDefaultMoveTest extends ContextTestSupport {
    private static final String TEST_FILE_NAME = "paris" + UUID.randomUUID() + ".txt";

    @Test
    public void testConsumeFromAbsolutePath() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:report");
        mock.expectedBodiesReceived("Hello Paris");
        mock.expectedFileExists(testFile(".camel/" + TEST_FILE_NAME));

        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .pollDelay(250, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    template.sendBodyAndHeader(fileUri(), "Hello Paris", Exchange.FILE_NAME, TEST_FILE_NAME);
                    assertMockEndpointsSatisfied();
                });
        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(fileUri("?initialDelay=0&delay=10"))
                        .convertBodyTo(String.class)
                        .to("mock:report");
            }
        };
    }
}
