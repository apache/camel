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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Isolated("Does not play well with parallel unit test execution")
public class FileChangedReadLockTest extends ContextTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(FileChangedReadLockTest.class);

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        testDirectory("in", true);
        super.setUp();
    }

    @Test
    public void testChangedReadLock() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedFileExists(testFile("out/slowfile.dat"));
        mock.expectedHeaderReceived(Exchange.FILE_LENGTH, expectedFileLength());

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

    long expectedFileLength() {
        long length = 0;
        for (int i = 0; i < 20; i++) {
            length += ("Line " + i + LS).getBytes().length;
        }
        return length;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUri("in?initialDelay=0&delay=10&readLock=changed&readLockCheckInterval=100"))
                        .to(fileUri("out"), "mock:result");
            }
        };
    }
}
