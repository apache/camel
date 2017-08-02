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
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;

/**
 * Unit test for the idempotent=true option.
 */
public class FileConsumerIdempotentTest extends ContextTestSupport {

    private String uri = "file://target/idempotent/?idempotent=true&move=done/${file:name}&initialDelay=0&delay=10";

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/idempotent");
        super.setUp();
        template.sendBodyAndHeader("file://target/idempotent", "Hello World", Exchange.FILE_NAME, "report.txt");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(uri)
                    .convertBodyTo(String.class).to("mock:result");
            }
        };
    }

    public void testIdempotent() throws Exception {
        // consume the file the first time
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        assertMockEndpointsSatisfied();

        oneExchangeDone.matchesMockWaitTime();

        // reset mock and set new expectations
        mock.reset();
        mock.expectedMessageCount(0);

        // move file back
        File file = new File("target/idempotent/done/report.txt");
        File renamed = new File("target/idempotent/report.txt");
        file.renameTo(renamed);

        // should NOT consume the file again, let a bit time pass to let the consumer try to consume it but it should not
        Thread.sleep(100);
        assertMockEndpointsSatisfied();

        FileEndpoint fe = context.getEndpoint(uri, FileEndpoint.class);
        assertNotNull(fe);

        MemoryIdempotentRepository repo = (MemoryIdempotentRepository) fe.getInProgressRepository();
        assertEquals("Should be no in-progress files", 0, repo.getCacheSize());
    }

}