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

import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Date;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FileChangedReadLockMinAgeTest extends ContextTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(FileChangedReadLockMinAgeTest.class);

    @Test
    public void testChangedReadLockMinAge() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedFileExists(testFile("out/slowfile.dat"));
        mock.expectedMessagesMatches(
                exchangeProperty(Exchange.RECEIVED_TIMESTAMP).convertTo(long.class).isGreaterThan(new Date().getTime() + 500));

        writeSlowFile();

        assertMockEndpointsSatisfied();

        String content = new String(Files.readAllBytes(testFile("out/slowfile.dat")));
        String[] lines = content.split(LS);
        assertEquals(20, lines.length, "There should be 20 lines in the file");
        for (int i = 0; i < 20; i++) {
            assertEquals("Line " + i, lines[i]);
        }
    }

    private void writeSlowFile() throws Exception {
        LOG.debug("Writing slow file...");

        try (OutputStream fos = Files.newOutputStream(testFile("in/slowfile.dat"))) {
            for (int i = 0; i < 20; i++) {
                fos.write(("Line " + i + LS).getBytes());
                LOG.debug("Writing line {}", i);
                Thread.sleep(50);
            }
            fos.flush();
        }
        LOG.debug("Writing slow file DONE...");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUri(
                        "in?initialDelay=0&delay=10&readLock=changed&readLockCheckInterval=100&readLockMinAge=1000&readLockTimeout=1500"))
                        .to(fileUri("out"), "mock:result");
            }
        };
    }
}
