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
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.WINDOWS)
public class FileLockExclusiveReadLockStrategyTest {

    @TempDir
    Path tempDir;

    private DefaultCamelContext camelContext;

    @BeforeEach
    void setUp() throws Exception {
        camelContext = new DefaultCamelContext();
        camelContext.start();
    }

    @AfterEach
    void tearDown() {
        if (camelContext != null) {
            camelContext.stop();
        }
    }

    @Test
    void testIOExceptionReturnsFalseAndCleansMarker() throws Exception {
        Path target = tempDir.resolve("testfile.txt");
        Files.writeString(target, "test content");

        // make the file read-only so RandomAccessFile("rw") throws IOException
        assertTrue(target.toFile().setWritable(false));

        FileLockExclusiveReadLockStrategy strategy = new FileLockExclusiveReadLockStrategy();
        strategy.setTimeout(10000);

        GenericFile<File> genericFile = new GenericFile<>();
        genericFile.setFile(target.toFile());
        genericFile.setAbsoluteFilePath(target.toAbsolutePath().toString());
        genericFile.setFileName(target.getFileName().toString());

        Exchange exchange = new DefaultExchange(camelContext);

        boolean acquired = strategy.acquireExclusiveReadLock(null, genericFile, exchange);

        assertFalse(acquired, "acquireExclusiveReadLock should return false when IOException occurs");

        File markerFile = new File(target.toAbsolutePath() + FileComponent.DEFAULT_LOCK_FILE_POSTFIX);
        assertFalse(markerFile.exists(), "marker file should be cleaned up after failed lock acquisition");

        // restore writable so @TempDir cleanup succeeds
        target.toFile().setWritable(true);
    }
}
