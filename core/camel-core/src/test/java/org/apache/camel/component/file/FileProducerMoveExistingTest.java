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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class FileProducerMoveExistingTest extends ContextTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/file");
        super.setUp();
    }

    @Test
    public void testExistingFileDoesNotExists() throws Exception {
        template.sendBodyAndHeader("file://target/data/file?fileExist=Move&moveExisting=${file:parent}/renamed-${file:onlyname}", "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertFileExists("target/data/file/hello.txt");
        assertFileNotExists("target/data/file/renamed-hello.txt");
    }

    @Test
    public void testExistingFileExists() throws Exception {
        template.sendBodyAndHeader("file://target/data/file?fileExist=Move&moveExisting=${file:parent}/renamed-${file:onlyname}", "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader("file://target/data/file?fileExist=Move&moveExisting=${file:parent}/renamed-${file:onlyname}", "Bye World", Exchange.FILE_NAME, "hello.txt");

        assertFileExists("target/data/file/hello.txt");
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, new File("target/data/file/hello.txt")));

        assertFileExists("target/data/file/renamed-hello.txt");
        assertEquals("Hello World", context.getTypeConverter().convertTo(String.class, new File("target/data/file/renamed-hello.txt")));
    }

    @Test
    public void testExistingFileExistsTempFileName() throws Exception {
        template.sendBodyAndHeader("file://target/data/file?tempFileName=${file:onlyname}.temp&fileExist=Move&moveExisting=${file:parent}/renamed-${file:onlyname}", "Hello World",
                                   Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader("file://target/data/file?tempFileName=${file:onlyname}.temp&fileExist=Move&moveExisting=${file:parent}/renamed-${file:onlyname}", "Bye World",
                                   Exchange.FILE_NAME, "hello.txt");

        assertFileExists("target/data/file/hello.txt");
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, new File("target/data/file/hello.txt")));

        assertFileExists("target/data/file/renamed-hello.txt");
        assertEquals("Hello World", context.getTypeConverter().convertTo(String.class, new File("target/data/file/renamed-hello.txt")));
    }

    @Test
    public void testExistingFileExistsMoveSubDir() throws Exception {
        template.sendBodyAndHeader("file://target/data/file?fileExist=Move&moveExisting=backup", "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader("file://target/data/file?fileExist=Move&moveExisting=backup", "Bye World", Exchange.FILE_NAME, "hello.txt");

        assertFileExists("target/data/file/hello.txt");
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, new File("target/data/file/hello.txt")));

        // would move into sub directory and keep existing name as is
        assertFileExists("target/data/file/backup/hello.txt");
        assertEquals("Hello World", context.getTypeConverter().convertTo(String.class, new File("target/data/file/backup/hello.txt")));
    }

    @Test
    public void testFailOnMoveExistingFileExistsEagerDeleteTrue() throws Exception {
        template.sendBodyAndHeader("file://target/data/file", "Old file", Exchange.FILE_NAME, "renamed-hello.txt");

        template.sendBodyAndHeader("file://target/data/file?fileExist=Move&moveExisting=${file:parent}/renamed-${file:onlyname}&eagerDeleteTargetFile=true", "Hello World",
                                   Exchange.FILE_NAME, "hello.txt");
        // we should be okay as we will just delete any existing file
        template.sendBodyAndHeader("file://target/data/file?fileExist=Move&moveExisting=${file:parent}/renamed-${file:onlyname}&eagerDeleteTargetFile=true", "Bye World",
                                   Exchange.FILE_NAME, "hello.txt");

        // we could write the new file so the old context should be there
        assertFileExists("target/data/file/hello.txt");
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, new File("target/data/file/hello.txt")));

        // and the renamed file should be overridden
        assertFileExists("target/data/file/renamed-hello.txt");
        assertEquals("Hello World", context.getTypeConverter().convertTo(String.class, new File("target/data/file/renamed-hello.txt")));
    }

    @Test
    public void testFailOnMoveExistingFileExistsEagerDeleteFalse() throws Exception {
        template.sendBodyAndHeader("file://target/data/file", "Old file", Exchange.FILE_NAME, "renamed-hello.txt");

        template.sendBodyAndHeader("file://target/data/file?fileExist=Move&moveExisting=${file:parent}/renamed-${file:onlyname}&eagerDeleteTargetFile=false", "Hello World",
                                   Exchange.FILE_NAME, "hello.txt");
        try {
            template.sendBodyAndHeader("file://target/data/file?fileExist=Move&moveExisting=${file:parent}/renamed-${file:onlyname}&eagerDeleteTargetFile=false", "Bye World",
                                       Exchange.FILE_NAME, "hello.txt");
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            GenericFileOperationFailedException cause = assertIsInstanceOf(GenericFileOperationFailedException.class, e.getCause());
            assertTrue(cause.getMessage().startsWith("Cannot move existing file"));
        }

        // we could not write the new file so the previous context should be
        // there
        assertFileExists("target/data/file/hello.txt");
        assertEquals("Hello World", context.getTypeConverter().convertTo(String.class, new File("target/data/file/hello.txt")));

        // and the renamed file should be untouched
        assertFileExists("target/data/file/renamed-hello.txt");
        assertEquals("Old file", context.getTypeConverter().convertTo(String.class, new File("target/data/file/renamed-hello.txt")));
    }

}
