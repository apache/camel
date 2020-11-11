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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.io.File.separator;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
public class FileProducerMoveExistingTest extends ContextTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/data/file");
        super.setUp();
    }

    @Test
    public void testExistingFileDoesNotExists() throws Exception {
        template.sendBodyAndHeader(
                "file://target/data/file?fileExist=Move&moveExisting=${file:parent}/renamed-${file:onlyname}", "Hello World",
                Exchange.FILE_NAME, "hello.txt");

        assertFileExists("target/data/file/hello.txt");
        assertFileNotExists("target/data/file/renamed-hello.txt");
    }

    @Test
    public void testExistingFileExists() throws Exception {
        template.sendBodyAndHeader(
                "file://target/data/file?fileExist=Move&moveExisting=${file:parent}/renamed-${file:onlyname}", "Hello World",
                Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader(
                "file://target/data/file?fileExist=Move&moveExisting=${file:parent}/renamed-${file:onlyname}", "Bye World",
                Exchange.FILE_NAME, "hello.txt");

        assertFileExists("target/data/file/hello.txt");
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, new File("target/data/file/hello.txt")));

        assertFileExists("target/data/file/renamed-hello.txt");
        assertEquals("Hello World",
                context.getTypeConverter().convertTo(String.class, new File("target/data/file/renamed-hello.txt")));
    }

    @Test
    public void testExistingFileExistsTempFileName() throws Exception {
        template.sendBodyAndHeader(
                "file://target/data/file?tempFileName=${file:onlyname}.temp&fileExist=Move&moveExisting=${file:parent}/renamed-${file:onlyname}",
                "Hello World",
                Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader(
                "file://target/data/file?tempFileName=${file:onlyname}.temp&fileExist=Move&moveExisting=${file:parent}/renamed-${file:onlyname}",
                "Bye World",
                Exchange.FILE_NAME, "hello.txt");

        assertFileExists("target/data/file/hello.txt");
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, new File("target/data/file/hello.txt")));

        assertFileExists("target/data/file/renamed-hello.txt");
        assertEquals("Hello World",
                context.getTypeConverter().convertTo(String.class, new File("target/data/file/renamed-hello.txt")));
    }

    @Test
    public void testExistingFileExistsTempFileName2() throws Exception {
        template.sendBodyAndHeader(
                "file://target/data/file?tempFileName=${file:onlyname}.temp&fileExist=Move&moveExisting=renamed",
                "Hello World",
                Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader(
                "file://target/data/file?tempFileName=${file:onlyname}.temp&fileExist=Move&moveExisting=renamed",
                "Bye World",
                Exchange.FILE_NAME, "hello.txt");

        assertFileExists("target/data/file/hello.txt");
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, new File("target/data/file/hello.txt")));

        assertFileExists("target/data/file/renamed/hello.txt");
        assertEquals("Hello World",
                context.getTypeConverter().convertTo(String.class, new File("target/data/file/renamed/hello.txt")));
    }

    @Test
    public void testFailOnMoveExistingFileExistsEagerDeleteFalseTempFileName() throws Exception {
        final String filename = "hello.txt";
        final String fileParent = "target" + "/" + "data" + "/" + "file";

        template.sendBodyAndHeader("file://target/data/file?tempFileName=${file:onlyname}.temp", "First File",
                Exchange.FILE_NAME,
                "renamed-hello.txt");

        template.sendBodyAndHeader(
                "file://target/data/file?tempFileName=${file:onlyname}.temp&fileExist=Move&moveExisting=${file:parent}/renamed-${file:onlyname}&eagerDeleteTargetFile=false",
                "Second File", Exchange.FILE_NAME, filename);
        // we should be okay as we will just delete any existing file
        template.sendBodyAndHeader(
                "file://target/data/file?tempFileName=${file:onlyname}.temp&fileExist=Move&moveExisting=${file:parent}/renamed-${file:onlyname}&eagerDeleteTargetFile=false",
                "Third File", Exchange.FILE_NAME, filename);

        // we could  write the new file so the old context should be moved
        assertFileExists(fileParent + "/" + filename);
        assertEquals("Third File",
                context.getTypeConverter().convertTo(String.class, new File(fileParent + "/" + filename)));

        // and the renamed file should not be overridden
        assertFileExists(fileParent + "/" + "renamed-" + filename);
        assertEquals("First File",
                context.getTypeConverter().convertTo(String.class, new File(fileParent + "/" + "renamed-" + filename)));
    }

    @Test
    public void testExistingFileExistsTempFileNameMoveDynamicSubdirFullPath() throws Exception {
        final String subdirPrefix = generateRandomString(5);
        final String dynamicPath = "${file:parent}/" + subdirPrefix + "-${date:now:yyyyMMddHHmmssSSS}/${file:onlyname}";
        final String tempFilename = "tempFileName=${file:onlyname}.temp&";
        testDynamicSubdir(subdirPrefix, dynamicPath, tempFilename);
    }

    @Test
    public void testExistingFileExistsTempFileNameMoveDynamicSubdir() throws Exception {
        final String subdirPrefix = generateRandomString(5);
        final String dynamicPath = subdirPrefix + "-${date:now:yyyyMMddHHmmssSSS}/";
        final String tempFilename = "tempFileName=${file:onlyname}.temp&";
        testDynamicSubdir(subdirPrefix, dynamicPath, tempFilename);
    }

    @Test
    public void testExistingFileExistsMoveDynamicSubdir() throws Exception {
        final String subdirPrefix = generateRandomString(5);
        final String dynamicPath = subdirPrefix + "-${date:now:yyyyMMddHHmmssSSS}/";
        final String tempFilename = "";
        testDynamicSubdir(subdirPrefix, dynamicPath, tempFilename);
    }

    @Test
    public void testExistingFileExistsMoveDynamicSubdirFullPath() throws Exception {
        final String subdirPrefix = generateRandomString(5);
        final String dynamicPath = "${file:parent}/" + subdirPrefix + "-${date:now:yyyyMMddHHmmssSSS}/${file:onlyname}";
        final String tempFilename = "";
        testDynamicSubdir(subdirPrefix, dynamicPath, tempFilename);
    }

    private void testDynamicSubdir(String subdirPrefix, String dynamicPath, String tempFilename) {
        final String filename = "howdy.txt";
        final String fileContent = "Hello World";
        final String fileParent = "target" + separator + "data" + separator + "file";
        template.sendBodyAndHeader("file://target/data/file?" + tempFilename + "fileExist=Move&moveExisting="
                                   + dynamicPath,
                fileContent, Exchange.FILE_NAME, filename);
        template.sendBodyAndHeader("file://target/data/file?" + tempFilename + "fileExist=Move&moveExisting="
                                   + dynamicPath,
                fileContent, Exchange.FILE_NAME, filename);
        assertFileExists(fileParent + separator + filename);
        assertEquals(fileContent,
                context.getTypeConverter().convertTo(String.class, new File(fileParent + separator + filename)));

        File folder = new File(fileParent);
        String[] directories = folder.list((current, name) -> {
            String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
            return new File(current, name).isDirectory() && name.startsWith(subdirPrefix + "-" + date);
        });
        assertEquals(1, directories.length);
        File movedFilePath = new File(fileParent + separator + directories[0] + separator + filename);

        assertTrue(movedFilePath.exists());
        assertEquals(fileContent, context.getTypeConverter().convertTo(String.class, movedFilePath));
    }

    @Test
    public void testExistingFileExistsTempFileNameMoveSubDir() throws Exception {

        final String filename = "howdy.txt";
        final String fileContent = "Hello World";
        final String fileParent = "target" + separator + "data" + separator + "file";
        final String archivePath = fileParent + separator + "archive";

        template.sendBodyAndHeader(
                "file://target/data/file?tempFileName=${file:onlyname}.temp&fileExist=Move&moveExisting=archive",
                fileContent, Exchange.FILE_NAME, filename);
        template.sendBodyAndHeader(
                "file://target/data/file?tempFileName=${file:onlyname}.temp&fileExist=Move&moveExisting=archive",
                fileContent, Exchange.FILE_NAME, filename);

        assertFileExists(fileParent + separator + filename);
        assertEquals(fileContent,
                context.getTypeConverter().convertTo(String.class, new File(fileParent + separator + filename)));

        assertFileExists(archivePath + separator + filename);
        assertEquals(fileContent,
                context.getTypeConverter().convertTo(String.class, new File(archivePath + separator + filename)));
    }

    @Test
    public void testExistingFileExistsTempFileNameRename() throws Exception {

        final String filename = "howdy.txt";
        final String fileContent1 = "Hello World1";
        final String fileContent2 = "Hello World2";

        final String fileParent = "target" + separator + "data" + separator + "file";

        template.sendBodyAndHeader(
                "file://target/data/file?tempFileName=${file:onlyname}.temp&fileExist=Move&moveExisting=${file:onlyname}.${date:now:yyyyMMddHHmmssSSS}",
                fileContent1, Exchange.FILE_NAME, filename);
        template.sendBodyAndHeader(
                "file://target/data/file?tempFileName=${file:onlyname}.temp&fileExist=Move&moveExisting=${file:onlyname}.${date:now:yyyyMMddHHmmssSSS}",
                fileContent2, Exchange.FILE_NAME, filename);

        File folder = new File(fileParent);
        String[] files = folder.list((current, name) -> {
            String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
            return new File(current, name).isFile() && name.startsWith(filename + "." + date);
        });
        assertEquals(1, files.length);
        File movedFilePath = new File(fileParent + separator + files[0]);

        assertTrue(movedFilePath.exists());
        assertEquals(fileContent1, context.getTypeConverter().convertTo(String.class, movedFilePath));
        assertEquals(fileContent2,
                context.getTypeConverter().convertTo(String.class, new File(fileParent + separator + filename)));

    }

    @Test
    public void testExistingFileExistsMoveSubDir() throws Exception {
        template.sendBodyAndHeader("file://target/data/file?fileExist=Move&moveExisting=backup", "Hello World",
                Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader("file://target/data/file?fileExist=Move&moveExisting=backup", "Bye World",
                Exchange.FILE_NAME, "hello.txt");

        assertFileExists("target/data/file/hello.txt");
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, new File("target/data/file/hello.txt")));

        // would move into sub directory and keep existing name as is
        assertFileExists("target/data/file/backup/hello.txt");
        assertEquals("Hello World",
                context.getTypeConverter().convertTo(String.class, new File("target/data/file/backup/hello.txt")));
    }

    @Test
    public void testFailOnMoveExistingFileExistsEagerDeleteTrue() throws Exception {
        template.sendBodyAndHeader("file://target/data/file", "Old file", Exchange.FILE_NAME, "renamed-hello.txt");

        template.sendBodyAndHeader(
                "file://target/data/file?fileExist=Move&moveExisting=${file:parent}/renamed-${file:onlyname}&eagerDeleteTargetFile=true",
                "Hello World",
                Exchange.FILE_NAME, "hello.txt");
        // we should be okay as we will just delete any existing file
        template.sendBodyAndHeader(
                "file://target/data/file?fileExist=Move&moveExisting=${file:parent}/renamed-${file:onlyname}&eagerDeleteTargetFile=true",
                "Bye World",
                Exchange.FILE_NAME, "hello.txt");

        // we could write the new file so the old context should be there
        assertFileExists("target/data/file/hello.txt");
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, new File("target/data/file/hello.txt")));

        // and the renamed file should be overridden
        assertFileExists("target/data/file/renamed-hello.txt");
        assertEquals("Hello World",
                context.getTypeConverter().convertTo(String.class, new File("target/data/file/renamed-hello.txt")));
    }

    @Test
    public void testFailOnMoveExistingFileExistsEagerDeleteFalse() throws Exception {
        template.sendBodyAndHeader("file://target/data/file", "Old file", Exchange.FILE_NAME, "renamed-hello.txt");

        template.sendBodyAndHeader(
                "file://target/data/file?fileExist=Move&moveExisting=${file:parent}/renamed-${file:onlyname}&eagerDeleteTargetFile=false",
                "Hello World",
                Exchange.FILE_NAME, "hello.txt");
        try {
            template.sendBodyAndHeader(
                    "file://target/data/file?fileExist=Move&moveExisting=${file:parent}/renamed-${file:onlyname}&eagerDeleteTargetFile=false",
                    "Bye World",
                    Exchange.FILE_NAME, "hello.txt");
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            GenericFileOperationFailedException cause
                    = assertIsInstanceOf(GenericFileOperationFailedException.class, e.getCause());
            assertTrue(cause.getMessage().startsWith("Cannot move existing file"));
        }

        // we could not write the new file so the previous context should be
        // there
        assertFileExists("target/data/file/hello.txt");
        assertEquals("Hello World", context.getTypeConverter().convertTo(String.class, new File("target/data/file/hello.txt")));

        // and the renamed file should be untouched
        assertFileExists("target/data/file/renamed-hello.txt");
        assertEquals("Old file",
                context.getTypeConverter().convertTo(String.class, new File("target/data/file/renamed-hello.txt")));
    }

    private String generateRandomString(int targetStringLength) {
        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        Random random = new Random();
        StringBuilder buffer = new StringBuilder(targetStringLength);
        for (int i = 0; i < targetStringLength; i++) {
            int randomLimitedInt = leftLimit + (int) (random.nextFloat() * (rightLimit - leftLimit + 1));
            buffer.append((char) randomLimitedInt);
        }
        return buffer.toString();
    }

}
