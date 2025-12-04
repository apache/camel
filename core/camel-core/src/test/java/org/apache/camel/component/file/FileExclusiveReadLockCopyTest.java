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
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

public class FileExclusiveReadLockCopyTest extends ContextTestSupport {

    public static final String FILE_QUERY = "?readLock=fileLock&initialDelay=0&delay=10";
    private static final String DEST = FileExclusiveReadLockCopyTest.class.getSimpleName();

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void testCopy() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        final Path path = testFile(DEST + File.separator + "hello.txt");
        mock.expectedFileExists(path, "Hello World");

        template.sendBodyAndHeader(fileUri(FILE_QUERY), "Hello World", Exchange.FILE_NAME, "hello.txt");

        // The file may have been created, but not yet flushed.
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(this::isFlushed);

        mock.assertIsSatisfied();
    }

    private boolean isFlushed() {
        final Path path = testFile(DEST + File.separator + "hello.txt");

        return path.toFile().exists() && "Hello World".length() == path.toFile().length();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(fileUri(FILE_QUERY)).to(fileUri(DEST)).to("mock:result");
            }
        };
    }
}
