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
package org.apache.camel.component.file.remote.integration;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.apache.camel.test.junit5.TestSupport.assertFileExists;
import static org.apache.camel.test.junit5.TestSupport.assertFileNotExists;
import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class FtpProducerMoveExistingIT extends FtpServerTestSupport {
    private String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}/move?password=admin&fileExist=Move";
    }

    @AfterEach
    public void cleanupDir() {
        Path moveToDir = service.getFtpRootDir().resolve("move");
        deleteDirectory(moveToDir);
    }

    @Test
    public void testExistingFileDoesNotExists() {
        template.sendBodyAndHeader(getFtpUrl() + "&moveExisting=${file:parent}/renamed-${file:onlyname}", "Hello World",
                Exchange.FILE_NAME, "hello.txt");

        assertFileExists(service.ftpFile("move/hello.txt"));
        assertFileNotExists(service.ftpFile("move/renamed-hello.txt"));
    }

    @Test
    public void testExistingFileExists() throws Exception {
        template.sendBodyAndHeader(getFtpUrl() + "&moveExisting=${file:parent}/renamed-${file:onlyname}", "Hello World",
                Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader(getFtpUrl() + "&moveExisting=${file:parent}/renamed-${file:onlyname}", "Bye World",
                Exchange.FILE_NAME, "hello.txt");

        assertFileExists(service.ftpFile("move/hello.txt"), "Bye World");
        assertFileExists(service.ftpFile("move/renamed-hello.txt"), "Hello World");
    }

    @Test
    public void testExistingFileExistsTempFilename() throws Exception {
        template.sendBodyAndHeader(
                getFtpUrl() + "&tempFileName=${file:onlyname}.temp&moveExisting=${file:parent}/renamed-${file:onlyname}",
                "Hello World", Exchange.FILE_NAME,
                "hello.txt");
        template.sendBodyAndHeader(
                getFtpUrl() + "&tempFileName=${file:onlyname}.temp&moveExisting=${file:parent}/renamed-${file:onlyname}",
                "Bye World", Exchange.FILE_NAME,
                "hello.txt");

        assertFileExists(service.ftpFile("move/hello.txt"), "Bye World");

        assertFileExists(service.ftpFile("move/renamed-hello.txt"), "Hello World");
    }

    @Test
    public void testExistingFileExistsTempFileNameMoveDynamicSubdir() throws Exception {
        final String subdirPrefix = generateRandomString(5);
        template.sendBodyAndHeader(getFtpUrl() + "&tempFileName=${file:onlyname}.temp&moveExisting=" + subdirPrefix
                                   + "-${date:now:yyyyMMddHHmmssSSS}/",
                "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader(getFtpUrl() + "&tempFileName=${file:onlyname}.temp&moveExisting=" + subdirPrefix
                                   + "-${date:now:yyyyMMddHHmmssSSS}/",
                "Bye World", Exchange.FILE_NAME, "hello.txt");

        assertFileExists(service.ftpFile("move/hello.txt"), "Bye World");

        File folder = service.ftpFile("move").toFile();
        String[] directories = folder.list(new FilenameFilter() {
            @Override
            public boolean accept(File current, String name) {
                String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
                return new File(current, name).isDirectory() && name.startsWith(subdirPrefix + "-" + date);
            }
        });
        assertEquals(1, directories.length);
        assertFileExists(service.ftpFile("move/" + directories[0] + "/hello.txt"), "Hello World");
    }

    @Test
    public void testExistingFileExistsTempFileNameMoveDynamicSubdirFullPath() throws Exception {
        final String subdirPrefix = generateRandomString(5);
        template.sendBodyAndHeader(getFtpUrl() + "&tempFileName=${file:onlyname}.temp&moveExisting=${file:parent}/"
                                   + subdirPrefix + "-${date:now:yyyyMMddHHmmssSSS}/${file:onlyname}",
                "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader(getFtpUrl() + "&tempFileName=${file:onlyname}.temp&moveExisting=${file:parent}/"
                                   + subdirPrefix + "-${date:now:yyyyMMddHHmmssSSS}/${file:onlyname}",
                "Bye World", Exchange.FILE_NAME, "hello.txt");
        assertFileExists(service.ftpFile("move/hello.txt"), "Bye World");

        File folder = service.ftpFile("move").toFile();
        String[] directories = folder.list(new FilenameFilter() {
            @Override
            public boolean accept(File current, String name) {
                String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
                return new File(current, name).isDirectory() && name.startsWith(subdirPrefix + "-" + date);
            }
        });
        assertEquals(1, directories.length);
        assertFileExists(service.ftpFile("move/" + directories[0] + "/hello.txt"), "Hello World");
    }

    @Test
    public void testExistingFileExistsTempFilenameMoveSubDir() throws Exception {
        template.sendBodyAndHeader(getFtpUrl() + "&tempFileName=${file:onlyname}.temp&moveExisting=archive", "Hello World",
                Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader(getFtpUrl() + "&tempFileName=${file:onlyname}.temp&moveExisting=archive", "Bye World",
                Exchange.FILE_NAME, "hello.txt");

        assertFileExists(service.ftpFile("move/hello.txt"), "Bye World");

        assertFileExists(service.ftpFile("move/archive/hello.txt"), "Hello World");
    }

    @Test
    public void testExistingFileExistsMoveSubDir() throws Exception {
        template.sendBodyAndHeader(getFtpUrl() + "&moveExisting=backup", "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader(getFtpUrl() + "&moveExisting=backup", "Bye World", Exchange.FILE_NAME, "hello.txt");

        assertFileExists(service.ftpFile("move/hello.txt"), "Bye World");

        // would move into sub directory and keep existing name as is
        assertFileExists(service.ftpFile("move/backup/hello.txt"), "Hello World");
    }

    @Test
    public void testFailOnMoveExistingFileExistsEagerDeleteTrue() throws Exception {
        template.sendBodyAndHeader(
                getFtpUrl() + "&moveExisting=${file:parent}/renamed-${file:onlyname}&eagerDeleteTargetFile=true", "Old file",
                Exchange.FILE_NAME,
                "renamed-hello.txt");

        template.sendBodyAndHeader(
                getFtpUrl() + "&moveExisting=${file:parent}/renamed-${file:onlyname}&eagerDeleteTargetFile=true", "Hello World",
                Exchange.FILE_NAME,
                "hello.txt");
        // we should be okay as we will just delete any existing file
        template.sendBodyAndHeader(
                getFtpUrl() + "&moveExisting=${file:parent}/renamed-${file:onlyname}&eagerDeleteTargetFile=true", "Bye World",
                Exchange.FILE_NAME, "hello.txt");

        // we could write the new file so the old context should be there
        assertFileExists(service.ftpFile("move/hello.txt"), "Bye World");

        // and the renamed file should be overridden
        assertFileExists(service.ftpFile("move/renamed-hello.txt"), "Hello World");
    }

    @Test
    public void testFailOnMoveExistingFileExistsEagerDeleteFalse() throws Exception {
        template.sendBodyAndHeader(
                getFtpUrl() + "&moveExisting=${file:parent}/renamed-${file:onlyname}&eagerDeleteTargetFile=true", "Old file",
                Exchange.FILE_NAME,
                "renamed-hello.txt");

        template.sendBodyAndHeader(
                getFtpUrl() + "&moveExisting=${file:parent}/renamed-${file:onlyname}&eagerDeleteTargetFile=false",
                "Hello World", Exchange.FILE_NAME,
                "hello.txt");

        String uri = getFtpUrl() + "&moveExisting=${file:parent}/renamed-${file:onlyname}&eagerDeleteTargetFile=false";
        Exception ex = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Bye World", Exchange.FILE_NAME, "hello.txt"));

        GenericFileOperationFailedException cause
                = assertIsInstanceOf(GenericFileOperationFailedException.class, ex.getCause());
        assertTrue(cause.getMessage().startsWith("Cannot move existing file"));

        // we could not write the new file so the previous context should be
        // there
        assertFileExists(service.ftpFile("move/hello.txt"), "Hello World");

        // and the renamed file should be untouched
        assertFileExists(service.ftpFile("move/renamed-hello.txt"), "Old file");
    }

    private String generateRandomString(int targetStringLength) {
        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        SecureRandom random = new SecureRandom();
        StringBuilder buffer = new StringBuilder(targetStringLength);
        for (int i = 0; i < targetStringLength; i++) {
            int randomLimitedInt = leftLimit + (int) (random.nextFloat() * (rightLimit - leftLimit + 1));
            buffer.append((char) randomLimitedInt);
        }
        return buffer.toString();
    }
}
