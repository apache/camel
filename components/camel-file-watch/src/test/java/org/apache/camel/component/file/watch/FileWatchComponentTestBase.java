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
package org.apache.camel.component.file.watch;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.watch.constants.FileEventEnum;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.util.AssertionErrors.assertTrue;

public class FileWatchComponentTestBase extends CamelTestSupport {

    @TempDir
    public Path folder;

    protected List<Path> testFiles = new ArrayList<>();

    protected String testMethod;

    static void assertFileEvent(String expectedFileName, FileEventEnum expectedEventType, Exchange exchange) {
        assertEquals(expectedFileName, exchange.getIn().getBody(File.class).getName());
        assertEquals(expectedEventType, exchange.getIn().getHeader(FileWatchConstants.EVENT_TYPE_HEADER, FileEventEnum.class));
    }

    public void beforeEach(ExtensionContext context) throws Exception {
        super.beforeEach(context);
        this.testMethod = context.getTestMethod().map(Method::getName).orElse("");
    }

    @Override
    protected void doPreSetup() throws Exception {
        cleanTestDir(new File(testPath()));
        new File(testPath()).mkdirs();
        for (int i = 0; i < 10; i++) {
            File newFile = new File(testPath(), testMethod + "-" + i);
            assumeTrue(newFile.createNewFile());
            testFiles.add(newFile.toPath());
        }
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        cleanTestDir(new File(testPath()));
    }

    private void cleanTestDir(File file) throws Exception {
        if (file == null || !file.exists() || file.listFiles() == null) {
            return;
        }
        for (File childFile : file.listFiles()) {
            if (childFile.isDirectory()) {
                cleanTestDir(childFile);
            } else {
                if (!childFile.delete()) {
                    throw new IOException();
                }
            }
        }

        if (!file.delete()) {
            throw new IOException();
        }
    }

    protected String testPath() {
        try {
            return folder.toRealPath()
                   + folder.getFileSystem().getSeparator()
                   + getClass().getSimpleName() + "_" + testMethod
                   + folder.getFileSystem().getSeparator();
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    protected File createFile(File parent, String child) {
        try {
            Files.createDirectories(parent.toPath());
            File newFile = new File(parent, child);
            assertTrue("File should be created but already exists", newFile.createNewFile());
            return newFile;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected File createFile(String parent, String child) {
        return createFile(new File(parent), child);
    }
}
