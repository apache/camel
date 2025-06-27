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
import org.junit.jupiter.api.Test;

/**
 * Unit test done files with moveFailed option
 */
public class FilerConsumerMoveFailedDoneFileNameTest extends ContextTestSupport {
    private static final String TEST_FILE_NAME = "hello" + UUID.randomUUID() + ".txt";

    @Test
    public void testDoneFile() throws Exception {
        getMockEndpoint("mock:input").expectedMessageCount(1);

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, TEST_FILE_NAME);
        template.sendBodyAndHeader(fileUri(), "", Exchange.FILE_NAME, "done");

        // wait a bit for the file processing to complete
        assertMockEndpointsSatisfied(1, TimeUnit.SECONDS);

        oneExchangeDone.matchesWaitTime();

        // done file should be deleted now
        assertFileNotExists(testFile("done"));

        // as well the original file should be moved to failed
        assertFileExists(testFile("failed/" + TEST_FILE_NAME));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?doneFileName=done&initialDelay=0&delay=10&moveFailed=failed")).to("mock:input")
                        .throwException(new IllegalArgumentException("Forced"));
            }
        };
    }

}
