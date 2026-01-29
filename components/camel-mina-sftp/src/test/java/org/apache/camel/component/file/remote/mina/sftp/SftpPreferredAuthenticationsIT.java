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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for preferredAuthentications configuration.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/sftp/hostkey.pem')")
public class SftpPreferredAuthenticationsIT extends SftpServerTestSupport {

    @Test
    public void testPreferredAuthenticationsPasswordFirst() {
        String preferredAuth = "password,publickey";
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile()
                     + "&preferredAuthentications=" + preferredAuth;

        template.sendBodyAndHeader(uri, "Password First Test", Exchange.FILE_NAME, "password-first.txt");

        File file = ftpFile("password-first.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Password First Test", context.getTypeConverter().convertTo(String.class, file));

        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        assertEquals(preferredAuth, endpoint.getConfiguration().getPreferredAuthentications());
    }

    @Test
    public void testPreferredAuthenticationsPublicKeyFirst() {
        String preferredAuth = "publickey,password";
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile()
                     + "&preferredAuthentications=" + preferredAuth;

        template.sendBodyAndHeader(uri, "PublicKey First Test", Exchange.FILE_NAME, "publickey-first.txt");

        File file = ftpFile("publickey-first.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("PublicKey First Test", context.getTypeConverter().convertTo(String.class, file));

        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        assertEquals(preferredAuth, endpoint.getConfiguration().getPreferredAuthentications());
    }

    @Test
    public void testPreferredAuthenticationsPasswordOnly() {
        String preferredAuth = "password";
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile()
                     + "&preferredAuthentications=" + preferredAuth;

        template.sendBodyAndHeader(uri, "Password Only Test", Exchange.FILE_NAME, "password-only.txt");

        File file = ftpFile("password-only.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Password Only Test", context.getTypeConverter().convertTo(String.class, file));

        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        assertEquals(preferredAuth, endpoint.getConfiguration().getPreferredAuthentications());
    }

    @Test
    public void testPreferredAuthenticationsWithKeyboardInteractive() {
        String preferredAuth = "keyboard-interactive,password,publickey";
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile()
                     + "&preferredAuthentications=" + preferredAuth;

        template.sendBodyAndHeader(uri, "Keyboard Interactive Test", Exchange.FILE_NAME, "keyboard-interactive.txt");

        File file = ftpFile("keyboard-interactive.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Keyboard Interactive Test", context.getTypeConverter().convertTo(String.class, file));

        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        assertEquals(preferredAuth, endpoint.getConfiguration().getPreferredAuthentications());
    }

    @Test
    public void testDefaultAuthenticationsWhenNotSpecified() {
        // When preferredAuthentications is not specified, default MINA SSHD order should be used
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile();

        template.sendBodyAndHeader(uri, "Default Auth Test", Exchange.FILE_NAME, "default-auth.txt");

        File file = ftpFile("default-auth.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Default Auth Test", context.getTypeConverter().convertTo(String.class, file));

        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        // preferredAuthentications should be null when not specified
        assertEquals(null, endpoint.getConfiguration().getPreferredAuthentications());
    }
}
