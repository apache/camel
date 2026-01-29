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

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.remote.mina.MinaSftpEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for chmod and chmodDirectory configuration.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/sftp/hostkey.pem')")
@EnabledOnOs(OS.LINUX) // chmod only works on POSIX systems
public class SftpChmodIT extends SftpServerTestSupport {

    @Test
    public void testChmodConfiguration() {
        // Test that chmod is correctly parsed and stored in configuration
        String chmod = "644";
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile()
                     + "&chmod=" + chmod;

        template.sendBodyAndHeader(uri, "Chmod Test", Exchange.FILE_NAME, "chmod-test.txt");

        File file = ftpFile("chmod-test.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Chmod Test", context.getTypeConverter().convertTo(String.class, file));

        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        assertEquals(chmod, endpoint.getConfiguration().getChmod());
    }

    @Test
    public void testChmodDirectoryConfiguration() {
        // Test that chmodDirectory is correctly parsed and stored in configuration
        String chmodDir = "755";
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir() + "/newdir"
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile()
                     + "&chmodDirectory=" + chmodDir;

        template.sendBodyAndHeader(uri, "ChmodDir Test", Exchange.FILE_NAME, "chmoddir-test.txt");

        File dir = ftpFile("newdir").toFile();
        assertTrue(dir.exists() && dir.isDirectory(), "Directory should exist: " + dir);

        File file = ftpFile("newdir/chmoddir-test.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("ChmodDir Test", context.getTypeConverter().convertTo(String.class, file));

        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        assertEquals(chmodDir, endpoint.getConfiguration().getChmodDirectory());
    }

    @Test
    public void testChmodAndChmodDirectoryCombined() {
        // Test both chmod and chmodDirectory together
        String chmod = "640";
        String chmodDir = "750";
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir() + "/combineddir"
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile()
                     + "&chmod=" + chmod + "&chmodDirectory=" + chmodDir;

        template.sendBodyAndHeader(uri, "Combined Test", Exchange.FILE_NAME, "combined-test.txt");

        File dir = ftpFile("combineddir").toFile();
        assertTrue(dir.exists() && dir.isDirectory(), "Directory should exist: " + dir);

        File file = ftpFile("combineddir/combined-test.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Combined Test", context.getTypeConverter().convertTo(String.class, file));

        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        assertEquals(chmod, endpoint.getConfiguration().getChmod());
        assertEquals(chmodDir, endpoint.getConfiguration().getChmodDirectory());
    }

    @Test
    public void testDefaultChmodNull() {
        // When chmod is not specified, it should be null
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile();

        template.sendBodyAndHeader(uri, "Default Test", Exchange.FILE_NAME, "default-chmod-test.txt");

        File file = ftpFile("default-chmod-test.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);

        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        assertNull(endpoint.getConfiguration().getChmod());
        assertNull(endpoint.getConfiguration().getChmodDirectory());
    }
}
