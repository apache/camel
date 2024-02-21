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
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class MarkerFileExclusiveReadLockStrategyUnlockTest extends ContextTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        testDirectory("input-a", true);
        testDirectory("input-b", true);
    }

    @Test
    public void testUnlocking() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();

        Files.write(testFile("input-a/file1.dat"), "File-1".getBytes());
        Files.write(testFile("input-b/file2.dat"), "File-2".getBytes());

        boolean done = notify.matches(5, TimeUnit.SECONDS);

        assertTrue(done, "Route should be done processing 1 exchanges");

        assertFileNotExists(testFile("input-a/file1.dat.camelLock"));
        assertFileNotExists(testFile("input-b/file2.dat.camelLock"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUri("input-a?fileName=file1.dat&readLock=markerFile&initialDelay=0&delay=10"))
                        .pollEnrich(fileUri("input-b?fileName=file2.dat&readLock=markerFile&initialDelay=0&delay=10"))
                        .to("mock:result");
            }
        };
    }

}
