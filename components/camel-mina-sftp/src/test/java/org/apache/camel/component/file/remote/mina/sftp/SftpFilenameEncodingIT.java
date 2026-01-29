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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for filenameEncoding configuration.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/sftp/hostkey.pem')")
public class SftpFilenameEncodingIT extends SftpServerTestSupport {

    @Test
    public void testFilenameEncodingConfiguration() {
        // Test that filenameEncoding is correctly parsed and stored in configuration
        String encoding = "UTF-8";
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile()
                     + "&filenameEncoding=" + encoding;

        template.sendBodyAndHeader(uri, "Encoding Test", Exchange.FILE_NAME, "encoding-test.txt");

        File file = ftpFile("encoding-test.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Encoding Test", context.getTypeConverter().convertTo(String.class, file));

        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        assertEquals(encoding, endpoint.getConfiguration().getFilenameEncoding());
    }

    @Test
    public void testFilenameEncodingISO88591() {
        // Test ISO-8859-1 encoding configuration
        String encoding = "ISO-8859-1";
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile()
                     + "&filenameEncoding=" + encoding;

        template.sendBodyAndHeader(uri, "Latin1 Test", Exchange.FILE_NAME, "latin1-test.txt");

        File file = ftpFile("latin1-test.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Latin1 Test", context.getTypeConverter().convertTo(String.class, file));

        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        assertEquals(encoding, endpoint.getConfiguration().getFilenameEncoding());
    }

    @Test
    public void testDefaultFilenameEncodingNull() {
        // When filenameEncoding is not specified, it should be null (MINA SSHD uses UTF-8 by default)
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile();

        template.sendBodyAndHeader(uri, "Default Encoding Test", Exchange.FILE_NAME, "default-encoding-test.txt");

        File file = ftpFile("default-encoding-test.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Default Encoding Test", context.getTypeConverter().convertTo(String.class, file));

        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        assertNull(endpoint.getConfiguration().getFilenameEncoding());
    }

    @Test
    public void testFilenameEncodingCombinedWithOtherOptions() {
        // Test filenameEncoding combined with other options
        String encoding = "UTF-8";
        String chmod = "644";
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile()
                     + "&filenameEncoding=" + encoding + "&chmod=" + chmod;

        template.sendBodyAndHeader(uri, "Combined Test", Exchange.FILE_NAME, "combined-encoding-test.txt");

        File file = ftpFile("combined-encoding-test.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Combined Test", context.getTypeConverter().convertTo(String.class, file));

        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        assertEquals(encoding, endpoint.getConfiguration().getFilenameEncoding());
        assertEquals(chmod, endpoint.getConfiguration().getChmod());
    }
}
