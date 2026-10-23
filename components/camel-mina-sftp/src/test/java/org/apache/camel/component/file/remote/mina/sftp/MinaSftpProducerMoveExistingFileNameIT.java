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
package org.apache.camel.component.file.remote.mina.sftp;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.apache.camel.test.junit6.TestSupport.assertFileExists;
import static org.apache.camel.test.junit6.TestSupport.assertIsInstanceOf;
import static org.apache.camel.test.junit6.TestSupport.createCleanDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * fileExist=Move: ${file:name} in moveExisting is the name of the file in its directory, as with the sftp (JSch) and
 * ftp components with their default stepwise=true, and not the path of the file from the login directory.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/sftp/hostkey.pem')")
class MinaSftpProducerMoveExistingFileNameIT extends SftpServerTestSupport {

    private String getFtpUrl() {
        return "mina-sftp://admin@localhost:{{ftp.server.port}}/{{ftp.root.dir}}/move?password=admin&fileExist=Move&knownHostsFile="
               + service.getKnownHostsFile();
    }

    @BeforeEach
    void cleanupDir() {
        createCleanDirectory(ftpFile("move"));
    }

    @Test
    void moveExistingIntoBackupDirectoryWithFileName() {
        send("&moveExisting=backup/${file:name}", "Hello World", "hello.txt");
        send("&moveExisting=backup/${file:name}", "Bye World", "hello.txt");

        assertEquals("Bye World", read("move/hello.txt"));
        assertEquals("Hello World", read("move/backup/hello.txt"));
    }

    @Test
    void moveExistingWithFileNamePrefix() {
        send("&moveExisting=old-${file:name}", "Hello World", "hello.txt");
        send("&moveExisting=old-${file:name}", "Bye World", "hello.txt");

        assertEquals("Bye World", read("move/hello.txt"));
        assertEquals("Hello World", read("move/old-hello.txt"));
    }

    @Test
    void moveExistingWithFileNameWithoutExtension() {
        send("&moveExisting=backup/${file:name.noext}.bak", "Hello World", "hello.txt");
        send("&moveExisting=backup/${file:name.noext}.bak", "Bye World", "hello.txt");

        assertEquals("Bye World", read("move/hello.txt"));
        assertEquals("Hello World", read("move/backup/hello.bak"));
    }

    @Test
    void moveExistingOfFileInSubDirectory() {
        send("&moveExisting=backup/${file:name}", "Hello World", "sub/hello.txt");
        send("&moveExisting=backup/${file:name}", "Bye World", "sub/hello.txt");

        assertEquals("Bye World", read("move/sub/hello.txt"));
        assertEquals("Hello World", read("move/sub/backup/hello.txt"));
    }

    @Test
    void moveExistingWithTempFileName() {
        send("&tempFileName=${file:onlyname}.tmp&moveExisting=backup/${file:name}", "Hello World", "hello.txt");
        send("&tempFileName=${file:onlyname}.tmp&moveExisting=backup/${file:name}", "Bye World", "hello.txt");

        assertEquals("Bye World", read("move/hello.txt"));
        assertEquals("Hello World", read("move/backup/hello.txt"));
    }

    @Test
    void moveExistingWithFileParentIsUnchanged() {
        send("&moveExisting=${file:parent}/renamed-${file:onlyname}", "Hello World", "hello.txt");
        send("&moveExisting=${file:parent}/renamed-${file:onlyname}", "Bye World", "hello.txt");

        assertEquals("Bye World", read("move/hello.txt"));
        assertEquals("Hello World", read("move/renamed-hello.txt"));
    }

    @Test
    void existingBackupIsNotOverwrittenWhenEagerDeleteIsDisabled() {
        send("&moveExisting=backup/${file:name}", "Old backup", "backup/hello.txt");
        send("&moveExisting=backup/${file:name}", "Hello World", "hello.txt");

        Exception ex = assertThrows(CamelExecutionException.class,
                () -> send("&moveExisting=backup/${file:name}&eagerDeleteTargetFile=false", "Bye World", "hello.txt"));
        assertIsInstanceOf(GenericFileOperationFailedException.class, ex.getCause());

        assertEquals("Hello World", read("move/hello.txt"));
        assertEquals("Old backup", read("move/backup/hello.txt"));
    }

    private void send(String options, String body, String fileName) {
        template.sendBodyAndHeader(getFtpUrl() + options, body, Exchange.FILE_NAME, fileName);
    }

    private String read(String file) {
        assertFileExists(ftpFile(file));
        return context.getTypeConverter().convertTo(String.class, ftpFile(file).toFile());
    }
}
