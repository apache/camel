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
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class FileRenameReadLockMustUseMarkerFileTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/rename");
        super.setUp();
    }

    public void testCamelLockFile() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Bye World");
        mock.message(0).header(Exchange.FILE_NAME).isEqualTo("bye.txt");

        template.sendBodyAndHeader("file:target/rename", "Bye World", Exchange.FILE_NAME, "bye.txt");

        // start the route
        context.startRoute("foo");

        assertMockEndpointsSatisfied();

        assertTrue(oneExchangeDone.matchesMockWaitTime());

        // and lock file should be deleted
        File lock = new File("target/rename/bye.txt" + FileComponent.DEFAULT_LOCK_FILE_POSTFIX);
        assertFalse("Lock file should not exist: " + lock, lock.exists());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/rename?readLock=rename&initialDelay=0&delay=10").routeId("foo").noAutoStartup()
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                // got a file, so we should have a .camelLock file as well
                                String name = exchange.getIn().getHeader(Exchange.FILE_PATH) + FileComponent.DEFAULT_LOCK_FILE_POSTFIX;
                                File lock = new File(name);

                                // lock file should exist
                                assertTrue("Lock file should exist: " + name, lock.exists());
                            }
                        })
                        .convertBodyTo(String.class).to("mock:result");
            }
        };
    }
}
