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
package org.apache.camel.component.file.remote.sftp.integration;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.apache.camel.test.junit5.TestSupport.assertFileExists;
import static org.apache.camel.test.junit5.TestSupport.assertFileNotExists;
import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.apache.camel.test.junit5.TestSupport.createCleanDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class SftpProducerMoveExistingIT extends SftpServerTestSupport {

    private String getFtpUrl() {
        return "sftp://admin@localhost:{{ftp.server.port}}/{{ftp.root.dir}}/move?password=admin&fileExist=Move&knownHostsFile="
               + service.getKnownHostsFile();
    }

    @BeforeEach
    public void cleanupDir() {
        createCleanDirectory(ftpFile("move"));
    }

    @Test
    public void testExistingFileDoesNotExists() {
        template.sendBodyAndHeader(getFtpUrl() + "&moveExisting=${file:parent}/renamed-${file:onlyname}", "Hello World",
                Exchange.FILE_NAME, "hello.txt");

        assertFileExists(ftpFile("move/hello.txt"));
        assertFileNotExists(ftpFile("move/renamed-hello.txt"));
    }

    @Test
    public void testExistingFileExists() {
        template.sendBodyAndHeader(getFtpUrl() + "&moveExisting=${file:parent}/renamed-${file:onlyname}", "Hello World",
                Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader(getFtpUrl() + "&moveExisting=${file:parent}/renamed-${file:onlyname}", "Bye World",
                Exchange.FILE_NAME, "hello.txt");

        assertFileExists(ftpFile("move/hello.txt"));
        assertEquals("Bye World",
                context.getTypeConverter().convertTo(String.class, ftpFile("move/hello.txt").toFile()));

        assertFileExists(ftpFile("move/renamed-hello.txt"));
        assertEquals("Hello World",
                context.getTypeConverter().convertTo(String.class,
                        ftpFile("move/renamed-hello.txt").toFile()));
    }

    @Test
    public void testExistingFileExistsMoveSubDir() {
        template.sendBodyAndHeader(getFtpUrl() + "&moveExisting=backup", "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader(getFtpUrl() + "&moveExisting=backup", "Bye World", Exchange.FILE_NAME, "hello.txt");

        assertFileExists(ftpFile("move/hello.txt"));
        assertEquals("Bye World",
                context.getTypeConverter().convertTo(String.class, ftpFile("move/hello.txt").toFile()));

        // would move into sub directory and keep existing name as is
        assertFileExists(ftpFile("move/backup/hello.txt"));
        assertEquals("Hello World",
                context.getTypeConverter().convertTo(String.class,
                        ftpFile("move/backup/hello.txt").toFile()));
    }

    @Test
    public void testFailOnMoveExistingFileExistsEagerDeleteTrue() {
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
        assertFileExists(ftpFile("move/hello.txt"));
        assertEquals("Bye World",
                context.getTypeConverter().convertTo(String.class, ftpFile("move/hello.txt").toFile()));

        // and the renamed file should be overridden
        assertFileExists(ftpFile("move/renamed-hello.txt"));
        assertEquals("Hello World",
                context.getTypeConverter().convertTo(String.class,
                        ftpFile("move/renamed-hello.txt").toFile()));
    }

    @Test
    public void testFailOnMoveExistingFileExistsEagerDeleteFalse() {
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
        assertFileExists(ftpFile("move/hello.txt"));
        assertEquals("Hello World",
                context.getTypeConverter().convertTo(String.class, ftpFile("move/hello.txt").toFile()));

        // and the renamed file should be untouched
        assertFileExists(ftpFile("move/renamed-hello.txt"));
        assertEquals("Old file",
                context.getTypeConverter().convertTo(String.class,
                        ftpFile("move/renamed-hello.txt").toFile()));
    }
}
