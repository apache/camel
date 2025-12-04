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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated("This test is regularly flaky")
public class MarkerFileExclusiveReadLockStrategyRecursiveCleanupTest extends ContextTestSupport {

    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        testDirectory("d1/d2/d3/d4/d5", true);

        createFiles("d1", "d1.dat");
        createFiles("d1/d2", "d2.dat");
        createFiles("d1/d2/d3", "d3.dat");
        createFiles("d1/d2/d3/d4", "d4.dat");
        createFiles("d1/d2/d3/d4/d5", "d5.dat");
    }

    @Test
    public void testNonRecursive() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri(
                                "d1?fileName=d1.dat&readLock=markerFile&readLockDeleteOrphanLockFiles=true&initialDelay=0&delay=10"))
                        .to("mock:result");
            }
        });
        context.start();

        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();
        boolean done = notify.matches(5, TimeUnit.SECONDS);
        assertTrue(done, "Route should be done processing 1 exchanges");
        checkFilesNotExists("d1", "d1.dat");
        checkFilesExists("d1/d2", "d2.dat");
        checkFilesExists("d1/d2/d3", "d3.dat");
        checkFilesExists("d1/d2/d3/d4", "d4.dat");
        checkFilesExists("d1/d2/d3/d4/d5", "d5.dat");
    }

    @Test
    public void testRecursiveSingleDepth() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri(
                                "d1?include=.*.dat&readLock=markerFile&readLockDeleteOrphanLockFiles=true&initialDelay=0&delay=10&recursive=true&minDepth=2&maxDepth=2"))
                        .to("mock:result");
            }
        });
        context.start();

        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();

        boolean done = notify.matches(5, TimeUnit.SECONDS);
        assertTrue(done, "Route should be done processing 1 exchanges");

        context.stop();

        checkFilesExists("d1", "d1.dat");
        checkFilesNotExists("d1/d2", "d2.dat");
        checkFilesExists("d1/d2/d3", "d3.dat");
        checkFilesExists("d1/d2/d3/d4", "d4.dat");
        checkFilesExists("d1/d2/d3/d4/d5", "d5.dat");
    }

    @Test
    public void testRecursiveRange() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri(
                                "d1?include=.*.dat&readLock=markerFile&readLockDeleteOrphanLockFiles=true&initialDelay=0&delay=10&recursive=true&minDepth=2&maxDepth=4"))
                        .to("mock:result");
            }
        });
        context.start();

        NotifyBuilder notify = new NotifyBuilder(context).whenDone(3).create();
        boolean done = notify.matches(5, TimeUnit.SECONDS);
        assertTrue(done, "Route should be done processing 3 exchanges");

        context.stop();

        checkFilesExists("d1", "d1.dat");
        checkFilesNotExists("d1/d2", "d2.dat");
        checkFilesNotExists("d1/d2/d3", "d3.dat");
        checkFilesNotExists("d1/d2/d3/d4", "d4.dat");
        checkFilesExists("d1/d2/d3/d4/d5", "d5.dat");
    }

    @Test
    public void testRecursiveRangeAntInclude() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri(
                                "d1?antInclude=**/*.dat&readLock=markerFile&readLockDeleteOrphanLockFiles=true&initialDelay=0&delay=10&recursive=true&minDepth=2&maxDepth=4"))
                        .to("mock:result");
            }
        });
        context.start();

        NotifyBuilder notify = new NotifyBuilder(context).whenDone(3).create();
        boolean done = notify.matches(5, TimeUnit.SECONDS);
        assertTrue(done, "Route should be done processing 3 exchanges");

        context.stop();

        checkFilesExists("d1", "d1.dat");
        checkFilesNotExists("d1/d2", "d2.dat");
        checkFilesNotExists("d1/d2/d3", "d3.dat");
        checkFilesNotExists("d1/d2/d3/d4", "d4.dat");
        checkFilesExists("d1/d2/d3/d4/d5", "d5.dat");
    }

    @Test
    public void testRecursive() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri(
                                "d1?include=.*.dat&readLock=markerFile&readLockDeleteOrphanLockFiles=true&initialDelay=0&delay=10&recursive=true"))
                        .to("mock:result");
            }
        });
        context.start();

        NotifyBuilder notify = new NotifyBuilder(context).whenDone(5).create();
        boolean done = notify.matches(5, TimeUnit.SECONDS);
        assertTrue(done, "Route should be done processing 5 exchanges");

        context.stop();

        checkFilesNotExists("d1", "d1.dat");
        checkFilesNotExists("d1/d2", "d2.dat");
        checkFilesNotExists("d1/d2/d3", "d3.dat");
        checkFilesNotExists("d1/d2/d3/d4", "d4.dat");
        checkFilesNotExists("d1/d2/d3/d4/d5", "d5.dat");
    }

    private void createFiles(String dir, String fileName) throws IOException {
        Files.write(testFile(dir + "/" + fileName), fileName.getBytes());
        Files.write(testFile(dir + "/" + fileName + FileComponent.DEFAULT_LOCK_FILE_POSTFIX), "".getBytes());
    }

    private void checkFilesExists(String dir, String fileName) {
        assertFileExists(testFile(dir + "/" + fileName));
        assertFileExists(testFile(dir + "/" + fileName + FileComponent.DEFAULT_LOCK_FILE_POSTFIX));
    }

    private void checkFilesNotExists(String dir, String fileName) {
        assertFileNotExists(testFile(dir + "/" + fileName));
        assertFileNotExists(testFile(dir + "/" + fileName + FileComponent.DEFAULT_LOCK_FILE_POSTFIX));
    }
}
