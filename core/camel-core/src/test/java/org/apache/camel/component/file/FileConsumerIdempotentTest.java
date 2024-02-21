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
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit test for the idempotent=true option.
 */
public class FileConsumerIdempotentTest extends ContextTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(fileUri("?idempotent=true&move=done/${file:name}&initialDelay=0&delay=10"))
                        .convertBodyTo(String.class).to("mock:result");
            }
        };
    }

    @Test
    public void testIdempotent() throws Exception {
        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "report.txt");

        // consume the file the first time
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        assertMockEndpointsSatisfied();

        oneExchangeDone.matchesWaitTime();

        // reset mock and set new expectations
        mock.reset();
        mock.expectedMessageCount(0);

        // move file back
        Files.move(testFile("done/report.txt"), testFile("report.txt"));

        // should NOT consume the file again, let a bit time pass to let the
        // consumer try to consume it but it should not
        Awaitility.await().pollDelay(100, TimeUnit.MILLISECONDS).untilAsserted(() -> assertMockEndpointsSatisfied());

        FileEndpoint fe = context.getEndpoint(fileUri(), FileEndpoint.class);
        assertNotNull(fe);

        MemoryIdempotentRepository repo = (MemoryIdempotentRepository) fe.getInProgressRepository();
        assertEquals(0, repo.getCacheSize(), "Should be no in-progress files");
    }

}
