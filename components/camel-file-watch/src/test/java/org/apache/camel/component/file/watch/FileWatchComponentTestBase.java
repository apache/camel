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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.watch.constants.FileEventEnum;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class FileWatchComponentTestBase extends CamelTestSupport {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder(Paths.get("target").toAbsolutePath().toFile());

    protected List<Path> testFiles = new ArrayList<>();

    static void assertFileEvent(String expectedFileName, FileEventEnum expectedEventType, Exchange exchange) {
        Assert.assertEquals(expectedFileName, exchange.getIn().getBody(File.class).getName());
        Assert.assertEquals(expectedEventType, exchange.getIn().getHeader(FileWatchComponent.EVENT_TYPE_HEADER, FileEventEnum.class));
    }

    static boolean isWindows() {
        //WatchService behaves differently on Windows (Emits both MODIFY and DELETE when file deleted)
        //see https://stackoverflow.com/questions/33753561/java-nio-watch-service-created-both-entry-create-and-entry-modify-when-a-new
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    @Override
    protected void doPreSetup() throws Exception {
        super.doPostSetup();
        cleanTestDir(new File(testPath()));
        new File(testPath()).mkdirs();
        for (int i = 0; i < 10; i++) {
            File newFile = new File(testPath(), getTestName().getMethodName() + "-" + i);
            Assume.assumeTrue(newFile.createNewFile());
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
        return folder.getRoot().getAbsolutePath()
            + folder.getRoot().toPath().getFileSystem().getSeparator()
            + getClass().getSimpleName() + "_" + getTestName().getMethodName()
            + folder.getRoot().toPath().getFileSystem().getSeparator();
    }

    protected File createFile(File parent, String child) {
        try {
            Files.createDirectories(parent.toPath());
            File newFile = new File(parent, child);
            Assert.assertTrue("File should be created but already exists", newFile.createNewFile());
            return newFile;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected File createFile(String parent, String child) {
        return createFile(new File(parent), child);
    }
}
