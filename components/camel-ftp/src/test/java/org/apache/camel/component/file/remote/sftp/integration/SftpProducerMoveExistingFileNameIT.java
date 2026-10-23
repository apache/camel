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
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.apache.camel.test.junit6.TestSupport.assertFileExists;
import static org.apache.camel.test.junit6.TestSupport.assertIsInstanceOf;
import static org.apache.camel.test.junit6.TestSupport.createCleanDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * fileExist=Move: ${file:name} in moveExisting is the name of the file in its directory, with and without stepwise
 * (without it the producer works with the path of the file from the login directory).
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
class SftpProducerMoveExistingFileNameIT extends SftpServerTestSupport {

    private String getFtpUrl(boolean stepwise) {
        return "sftp://admin@localhost:{{ftp.server.port}}/{{ftp.root.dir}}/move?password=admin&fileExist=Move&knownHostsFile="
               + service.getKnownHostsFile() + "&stepwise=" + stepwise;
    }

    @BeforeEach
    void cleanupDir() {
        createCleanDirectory(ftpFile("move"));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void moveExistingIntoBackupDirectoryWithFileName(boolean stepwise) {
        send(stepwise, "&moveExisting=backup/${file:name}", "Hello World", "hello.txt");
        send(stepwise, "&moveExisting=backup/${file:name}", "Bye World", "hello.txt");

        assertEquals("Bye World", read("move/hello.txt"));
        assertEquals("Hello World", read("move/backup/hello.txt"));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void moveExistingWithFileNamePrefix(boolean stepwise) {
        send(stepwise, "&moveExisting=old-${file:name}", "Hello World", "hello.txt");
        send(stepwise, "&moveExisting=old-${file:name}", "Bye World", "hello.txt");

        assertEquals("Bye World", read("move/hello.txt"));
        assertEquals("Hello World", read("move/old-hello.txt"));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void moveExistingWithFileNameWithoutExtension(boolean stepwise) {
        send(stepwise, "&moveExisting=backup/${file:name.noext}.bak", "Hello World", "hello.txt");
        send(stepwise, "&moveExisting=backup/${file:name.noext}.bak", "Bye World", "hello.txt");

        assertEquals("Bye World", read("move/hello.txt"));
        assertEquals("Hello World", read("move/backup/hello.bak"));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void moveExistingOfFileInSubDirectory(boolean stepwise) {
        send(stepwise, "&moveExisting=backup/${file:name}", "Hello World", "sub/hello.txt");
        send(stepwise, "&moveExisting=backup/${file:name}", "Bye World", "sub/hello.txt");

        assertEquals("Bye World", read("move/sub/hello.txt"));
        assertEquals("Hello World", read("move/sub/backup/hello.txt"));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void moveExistingWithTempFileName(boolean stepwise) {
        send(stepwise, "&tempFileName=${file:onlyname}.tmp&moveExisting=backup/${file:name}", "Hello World", "hello.txt");
        send(stepwise, "&tempFileName=${file:onlyname}.tmp&moveExisting=backup/${file:name}", "Bye World", "hello.txt");

        assertEquals("Bye World", read("move/hello.txt"));
        assertEquals("Hello World", read("move/backup/hello.txt"));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void existingBackupIsNotOverwrittenWhenEagerDeleteIsDisabled(boolean stepwise) {
        send(stepwise, "&moveExisting=backup/${file:name}", "Old backup", "backup/hello.txt");
        send(stepwise, "&moveExisting=backup/${file:name}", "Hello World", "hello.txt");

        Exception ex = assertThrows(CamelExecutionException.class,
                () -> send(stepwise, "&moveExisting=backup/${file:name}&eagerDeleteTargetFile=false", "Bye World",
                        "hello.txt"));
        assertIsInstanceOf(GenericFileOperationFailedException.class, ex.getCause());

        assertEquals("Hello World", read("move/hello.txt"));
        assertEquals("Old backup", read("move/backup/hello.txt"));
    }

    private void send(boolean stepwise, String options, String body, String fileName) {
        template.sendBodyAndHeader(getFtpUrl(stepwise) + options, body, Exchange.FILE_NAME, fileName);
    }

    private String read(String file) {
        assertFileExists(ftpFile(file));
        return context.getTypeConverter().convertTo(String.class, ftpFile(file).toFile());
    }
}
