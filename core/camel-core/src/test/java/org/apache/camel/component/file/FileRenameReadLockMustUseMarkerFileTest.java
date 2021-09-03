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

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileRenameReadLockMustUseMarkerFileTest extends ContextTestSupport {

    @Test
    public void testCamelLockFile() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Bye World");
        mock.message(0).header(Exchange.FILE_NAME).isEqualTo("bye.txt");

        template.sendBodyAndHeader(fileUri(), "Bye World", Exchange.FILE_NAME, "bye.txt");

        // start the route
        context.getRouteController().startRoute("foo");

        assertMockEndpointsSatisfied();

        assertTrue(oneExchangeDone.matchesWaitTime());

        // and lock file should be deleted
        assertFileNotExists(testFile("bye.txt" + FileComponent.DEFAULT_LOCK_FILE_POSTFIX));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUri("?readLock=rename&initialDelay=0&delay=10")).routeId("foo").noAutoStartup()
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                // got a file, so we should have a .camelLock file as
                                // well
                                String name = exchange.getIn().getHeader(Exchange.FILE_PATH)
                                              + FileComponent.DEFAULT_LOCK_FILE_POSTFIX;
                                File lock = new File(name);

                                // lock file should exist
                                assertTrue(lock.exists(), "Lock file should exist: " + name);
                            }
                        }).convertBodyTo(String.class).to("mock:result");
            }
        };
    }
}
