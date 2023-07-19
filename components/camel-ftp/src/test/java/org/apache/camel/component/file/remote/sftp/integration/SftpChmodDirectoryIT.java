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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;

import org.apache.camel.Exchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class SftpChmodDirectoryIT extends SftpServerTestSupport {

    //@Test
    void testSftpChmodDirectoryWriteable() {

        template.sendBodyAndHeader(
                "sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}/folder" +
                                   "?username=admin&password=admin&chmod=777&chmodDirectory=770",
                "Hello World", Exchange.FILE_NAME,
                "hello.txt");


        File path = ftpFile("folder/hello.txt").getParent().toFile();
        assertTrue(path.canRead(), "Path should have permission readable: " + path);
        assertTrue(path.canWrite(), "Path should have permission writeable: " + path);
    }

    private Path testDir;

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        testDir = Paths.get(ftpFile("test").toString());
        try {
            Files.createDirectories(testDir);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Create directory structure ../../../subdir/dir
        Paths.get(testDir.toString(), "restricted/subdir").toFile().mkdirs();

        // Set permission to subdir directory
        File subdir = Paths.get(testDir.toString(), "restricted").toFile();
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_EXECUTE);
        Files.setPosixFilePermissions(subdir.toPath(), perms);

    }

    @AfterEach
    public void tearDown() throws IOException {
        // Reset permissions before deletion
//        Files.walk(testDir).forEach(path -> {
//            var perms = new HashSet<>(Set.of(PosixFilePermission.values()));
//            try {
//                Files.setPosixFilePermissions(path, perms);
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//        });

        // Ensure temporary test directory is deleted after test
        Files.walk(testDir)
                .map(Path::toFile)
                .sorted((o1, o2) -> -o1.compareTo(o2))
                .forEach(File::delete);
    }


    @Test
    public void testSftpChmodRelativeDirectoryWriteable() {

        template.sendBodyAndHeader(
                "sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}/test/restricted/subdir/first/second" +
                        "?username=admin&password=admin&chmod=777&chmodDirectory=770",
                "Hello World", Exchange.FILE_NAME,
                "hello.txt");

        File path = ftpFile("test/restricted/subdir/first/second/hello.txt").getParent().toFile();
        assertTrue(path.canRead(), "Path should have permission readable: " + path);
        assertTrue(path.canWrite(), "Path should have permission writeable: " + path);
    }

}
