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

import java.nio.file.Files;
import java.util.Date;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileChangedReadLockMinAgeShortCircuitTest extends ContextTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(FileChangedReadLockMinAgeShortCircuitTest.class);

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        testDirectory("in", true);
        LOG.debug("Writing file...");
        Files.write(testFile("in/file.dat"), "Line".getBytes());
        LOG.debug("Writing file DONE...");
    }

    @Test
    public void testChangedReadLockMinAge() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedFileExists(testFile("out/file.dat"));
        // We should get the file on the first poll
        mock.expectedMessagesMatches(
                exchangeProperty(Exchange.RECEIVED_TIMESTAMP).convertTo(long.class).isLessThan(new Date().getTime() + 15000));

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUri(
                        "in?initialDelay=500&delay=10&readLock=changed&readLockMinAge=10&readLockCheckInterval=30000&readLockTimeout=90000"))
                                .to(fileUri("out"), "mock:result");
            }
        };
    }
}
