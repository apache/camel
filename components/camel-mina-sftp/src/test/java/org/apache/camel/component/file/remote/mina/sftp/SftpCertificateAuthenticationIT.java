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
import java.nio.file.Files;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.remote.mina.MinaSftpConfiguration;
import org.apache.camel.component.file.remote.mina.MinaSftpEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for OpenSSH certificate-based authentication.
 * <p>
 * These tests verify that certificate authentication works for SFTP operations. The embedded server accepts all public
 * keys, so the tests validate that the client correctly loads and uses the certificate alongside the private key.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/sftp/hostkey.pem')")
public class SftpCertificateAuthenticationIT extends SftpServerTestSupport {

    private static final String TEST_RESOURCES = "src/test/resources/";

    @Test
    public void testCertificateAuthenticationWithCertFile() throws Exception {
        // Test uploading a file using certificate authentication with certFile option
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&knownHostsFile=" + service.getKnownHostsFile()
                     + "&privateKeyFile=" + TEST_RESOURCES + "test-key-rsa"
                     + "&certFile=" + TEST_RESOURCES + "test-key-rsa-cert.pub";

        template.sendBodyAndHeader(uri, "Certificate File Upload Test", Exchange.FILE_NAME, "cert-file-upload.txt");

        File uploadedFile = ftpFile("cert-file-upload.txt").toFile();
        assertTrue(uploadedFile.exists(), "File should be uploaded with certificate auth (certFile)");
        assertEquals("Certificate File Upload Test", context.getTypeConverter().convertTo(String.class, uploadedFile));
    }

    @Test
    public void testCertificateAuthenticationWithCertUri() throws Exception {
        // Test uploading a file using certificate authentication with certUri option
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&knownHostsFile=" + service.getKnownHostsFile()
                     + "&privateKeyUri=file:" + TEST_RESOURCES + "test-key-rsa"
                     + "&certUri=file:" + TEST_RESOURCES + "test-key-rsa-cert.pub";

        template.sendBodyAndHeader(uri, "Certificate URI Upload Test", Exchange.FILE_NAME, "cert-uri-upload.txt");

        File uploadedFile = ftpFile("cert-uri-upload.txt").toFile();
        assertTrue(uploadedFile.exists(), "File should be uploaded with certificate auth (certUri)");
        assertEquals("Certificate URI Upload Test", context.getTypeConverter().convertTo(String.class, uploadedFile));
    }

    @Test
    public void testCertificateAuthenticationWithCertBytes() throws Exception {
        // Load certificate and key bytes
        byte[] certBytes = Files.readAllBytes(new File(TEST_RESOURCES + "test-key-rsa-cert.pub").toPath());
        byte[] keyBytes = Files.readAllBytes(new File(TEST_RESOURCES + "test-key-rsa").toPath());

        // Configure endpoint programmatically with byte arrays
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&knownHostsFile=" + service.getKnownHostsFile();

        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        MinaSftpConfiguration config = (MinaSftpConfiguration) endpoint.getConfiguration();
        config.setCertBytes(certBytes);
        config.setPrivateKey(keyBytes);

        // Upload file using certificate authentication with certBytes
        template.sendBodyAndHeader(endpoint, "Certificate Bytes Upload Test", Exchange.FILE_NAME, "cert-bytes-upload.txt");

        // Verify file was uploaded
        File uploadedFile = ftpFile("cert-bytes-upload.txt").toFile();
        assertTrue(uploadedFile.exists(), "File should be uploaded with certificate auth (certBytes)");
        assertEquals("Certificate Bytes Upload Test", context.getTypeConverter().convertTo(String.class, uploadedFile));
    }

    @Test
    public void testCertificatePriorityBytesOverFile() throws Exception {
        // certBytes should take priority over certFile
        byte[] certBytes = Files.readAllBytes(new File(TEST_RESOURCES + "test-key-rsa-cert.pub").toPath());
        byte[] keyBytes = Files.readAllBytes(new File(TEST_RESOURCES + "test-key-rsa").toPath());

        // Configure endpoint with both certBytes and certFile
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&knownHostsFile=" + service.getKnownHostsFile()
                     + "&certFile=non-existent-cert.pub"; // This would fail if used

        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        MinaSftpConfiguration config = (MinaSftpConfiguration) endpoint.getConfiguration();
        config.setCertBytes(certBytes); // This takes priority
        config.setPrivateKey(keyBytes);

        // Should succeed because certBytes takes priority over certFile
        template.sendBodyAndHeader(endpoint, "Priority Test", Exchange.FILE_NAME, "cert-priority-upload.txt");

        File uploadedFile = ftpFile("cert-priority-upload.txt").toFile();
        assertTrue(uploadedFile.exists(), "File should be uploaded (certBytes takes priority over certFile)");
        assertEquals("Priority Test", context.getTypeConverter().convertTo(String.class, uploadedFile));
    }
}
