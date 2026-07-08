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
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * Unit test for writing done files
 */
public class FilerConsumerDualDoneFileNameTest extends ContextTestSupport {
    private static final String TEST_FILE_NAME_1 = "hello" + UUID.randomUUID() + ".txt";
    private static final String TEST_FILE_NAME_2 = "bye" + UUID.randomUUID() + ".txt";

    @Test
    public void testTwoDoneFile() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceivedInAnyOrder("Hello World", "Bye World");

        template.sendBodyAndHeader(fileUri("?doneFileName=${file:name}.ready"), "Hello World", Exchange.FILE_NAME,
                TEST_FILE_NAME_1);
        template.sendBodyAndHeader(fileUri("?doneFileName=${file:name}.ready"), "Bye World", Exchange.FILE_NAME,
                TEST_FILE_NAME_2);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOneDoneFileMissing() {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader(fileUri("?doneFileName=${file:name}.ready"), "Hello World", Exchange.FILE_NAME,
                TEST_FILE_NAME_1);
        template.sendBodyAndHeader(fileUri(), "Bye World", Exchange.FILE_NAME, TEST_FILE_NAME_2);

        // give chance to poll 2nd file but it lacks the done file
        Awaitility.await().pollDelay(250, TimeUnit.MILLISECONDS).untilAsserted(() -> assertMockEndpointsSatisfied());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?doneFileName=${file:name}.ready&initialDelay=0&delay=10")).to("mock:result");
            }
        };
    }

}
