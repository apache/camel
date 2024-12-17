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
import org.apache.camel.Processor;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FromFilePollThirdTimeOkTest extends ContextTestSupport {
    private static final String TEST_FILE_NAME = "hello" + UUID.randomUUID() + ".txt";

    private static int counter;

    @Test
    public void testPollFileAndShouldBeDeletedAtThirdPoll() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(3).create();

        String body = "Hello World this file will be deleted";
        template.sendBodyAndHeader(fileUri(), body, Exchange.FILE_NAME, TEST_FILE_NAME);
        context.getRouteController().startRoute("FromFilePollThirdTimeOkTest");

        getMockEndpoint("mock:result").expectedBodiesReceived(body);

        assertMockEndpointsSatisfied();
        assertTrue(notify.matchesWaitTime());
        assertEquals(3, counter);

        // assert the file is deleted
        assertFileNotExists(testFile(TEST_FILE_NAME));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(fileUri("?delete=true&initialDelay=0&delay=10")).autoStartup(false)
                        .routeId("FromFilePollThirdTimeOkTest").process(new Processor() {
                            public void process(Exchange exchange) {
                                counter++;
                                if (counter < 3) {
                                    // file should exists
                                    assertFileExists(testFile(TEST_FILE_NAME));
                                    throw new IllegalArgumentException("Forced by unittest");
                                }
                            }
                        }).convertBodyTo(String.class).to("mock:result");
            }
        };
    }

}
