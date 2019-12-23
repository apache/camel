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
package org.apache.camel.component.file.strategy;

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

public class FileChangedReadLockZeroTimeoutTest extends ContextTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/changed/in");
        createDirectory("target/data/changed/out");
        super.setUp();
    }

    @Test
    public void testChangedReadLockZeroTimeout() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedFileExists("target/data/changed/out/hello1.txt");

        template.sendBodyAndHeader("file://target/data/changed/in", "Hello World", Exchange.FILE_NAME, "hello1.txt");

        Thread.sleep(100);

        File file = new File("target/data/changed/in/hello1.txt");
        assertTrue(file.delete());

        mock.reset();
        oneExchangeDone.reset();
        mock.expectedMessageCount(1);
        mock.expectedFileExists("target/data/changed/out/hello2.txt");

        template.sendBodyAndHeader("file://target/data/changed/in", "Hello Again World", Exchange.FILE_NAME, "hello2.txt");

        assertMockEndpointsSatisfied();
        oneExchangeDone.matchesMockWaitTime();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/data/changed/in?initialDelay=0&delay=10&readLock=changed&readLockCheckInterval=5000&readLockTimeout=0").to("file:target/data/changed/out",
                                                                                                                                             "mock:result");
            }
        };
    }

}
