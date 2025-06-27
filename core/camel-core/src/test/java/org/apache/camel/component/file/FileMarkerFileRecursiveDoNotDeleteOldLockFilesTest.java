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
import org.junit.jupiter.api.Test;

public class FileMarkerFileRecursiveDoNotDeleteOldLockFilesTest extends ContextTestSupport {
    private static final String TEST_FILE_NAME_1 = "hello" + UUID.randomUUID() + ".txt";
    private static final String TEST_FILE_NAME_2 = "gooday" + UUID.randomUUID() + ".txt";
    private static final String TEST_FILE_NAME_3 = "new" + UUID.randomUUID() + ".txt";

    @Test
    public void testDeleteOldLockOnStartup() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("New World");

        template.sendBodyAndHeader(fileUri(), "locked", Exchange.FILE_NAME,
                TEST_FILE_NAME_1 + FileComponent.DEFAULT_LOCK_FILE_POSTFIX);
        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, TEST_FILE_NAME_1);
        template.sendBodyAndHeader(fileUri("foo"), "locked", Exchange.FILE_NAME,
                TEST_FILE_NAME_2 + FileComponent.DEFAULT_LOCK_FILE_POSTFIX);
        template.sendBodyAndHeader(fileUri("foo"), "Goodday World", Exchange.FILE_NAME, TEST_FILE_NAME_2);
        // and a new file that has no lock
        template.sendBodyAndHeader(fileUri(), "New World", Exchange.FILE_NAME, TEST_FILE_NAME_3);

        // start the route
        context.getRouteController().startRoute("foo");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?initialDelay=0&delay=10&readLock=markerFile&readLockDeleteOrphanLockFiles=false&recursive=true"))
                        .routeId("foo").autoStartup(false)
                        .convertBodyTo(String.class).to("log:result", "mock:result");
            }
        };
    }
}
