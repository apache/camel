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
 * Integration tests for bindAddress configuration.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/sftp/hostkey.pem')")
public class SftpBindAddressIT extends SftpServerTestSupport {

    @Test
    public void testBindAddressConfiguration() {
        // Test that bindAddress is correctly parsed and stored in configuration
        // Using 127.0.0.1 as it's always available on localhost
        String bindAddr = "127.0.0.1";
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile()
                     + "&bindAddress=" + bindAddr;

        template.sendBodyAndHeader(uri, "Bind Address Test", Exchange.FILE_NAME, "bind-address-test.txt");

        File file = ftpFile("bind-address-test.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Bind Address Test", context.getTypeConverter().convertTo(String.class, file));

        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        assertEquals(bindAddr, endpoint.getConfiguration().getBindAddress());
    }

    @Test
    public void testDefaultBindAddressNull() {
        // When bindAddress is not specified, it should be null (OS default routing)
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile();

        template.sendBodyAndHeader(uri, "Default Bind Test", Exchange.FILE_NAME, "default-bind-test.txt");

        File file = ftpFile("default-bind-test.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Default Bind Test", context.getTypeConverter().convertTo(String.class, file));

        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        assertNull(endpoint.getConfiguration().getBindAddress());
    }

    @Test
    public void testBindAddressWithIPv6Loopback() {
        // Test IPv6 loopback address
        String bindAddr = "::1";
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile()
                     + "&bindAddress=" + bindAddr;

        // Just verify the configuration is correctly parsed
        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        assertEquals(bindAddr, endpoint.getConfiguration().getBindAddress());
    }

    @Test
    public void testBindAddressWithHostname() {
        // Test with localhost hostname
        String bindAddr = "localhost";
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile()
                     + "&bindAddress=" + bindAddr;

        template.sendBodyAndHeader(uri, "Hostname Bind Test", Exchange.FILE_NAME, "hostname-bind-test.txt");

        File file = ftpFile("hostname-bind-test.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Hostname Bind Test", context.getTypeConverter().convertTo(String.class, file));

        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        assertEquals(bindAddr, endpoint.getConfiguration().getBindAddress());
    }

    @Test
    public void testBindAddressWithPort() {
        // Test IPv4 address with explicit port (using 0 for ephemeral to avoid conflicts)
        // This is a mina-sftp specific feature not available in JSch-based sftp component
        String bindAddr = "127.0.0.1:0";
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile()
                     + "&bindAddress=" + bindAddr;

        template.sendBodyAndHeader(uri, "Bind With Port Test", Exchange.FILE_NAME, "bind-with-port-test.txt");

        File file = ftpFile("bind-with-port-test.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Bind With Port Test", context.getTypeConverter().convertTo(String.class, file));

        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        assertEquals(bindAddr, endpoint.getConfiguration().getBindAddress());
    }

    @Test
    public void testBindAddressWithHostnameAndPort() {
        // Test hostname with explicit port
        String bindAddr = "localhost:0";
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile()
                     + "&bindAddress=" + bindAddr;

        template.sendBodyAndHeader(uri, "Hostname Port Test", Exchange.FILE_NAME, "hostname-port-test.txt");

        File file = ftpFile("hostname-port-test.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Hostname Port Test", context.getTypeConverter().convertTo(String.class, file));

        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        assertEquals(bindAddr, endpoint.getConfiguration().getBindAddress());
    }

    @Test
    public void testBindAddressIPv6WithPort() {
        // Test IPv6 address with port using bracketed notation
        // Format: [ipv6]:port
        String bindAddr = "[::1]:0";
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile()
                     + "&bindAddress=" + bindAddr;

        // Just verify the configuration is correctly parsed
        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        assertEquals(bindAddr, endpoint.getConfiguration().getBindAddress());
    }
}
