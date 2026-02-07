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
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.component.file.remote.mina.MinaSftpEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/sftp/hostkey.pem')")
public class SftpKeyExchangeProtocolsIT extends SftpServerTestSupport {

    @Test
    public void testSftpSetKeyExchangeProtocol() {
        String kex = "ecdh-sha2-nistp256";
        String uri
                = "mina-sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}?username=admin&password=admin&keyExchangeProtocols="
                  + kex + "&knownHostsFile=" + service.getKnownHostsFile();
        template.sendBodyAndHeader(uri, "Hello World", Exchange.FILE_NAME, "hello-kex.txt");

        // test setting the key exchange protocol doesn't interfere with message payload
        File file = ftpFile("hello-kex.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Hello World", context.getTypeConverter().convertTo(String.class, file));

        // did we actually set the correct key exchange protocol?
        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        assertAll("Verify chipers:",
                () -> assertEquals(1, endpoint.getConfiguration().getKeyExchangeProtocols().size(),
                        "List of chiphers should contain exactly one element"),
                () -> assertEquals(kex, endpoint.getConfiguration().getKeyExchangeProtocols().get(0),
                        "The single chipher should match"));
    }

    @Test
    public void testSftpSetMultipleKeyExchangeProtocols() {
        List<String> expectedKex = List.of("curve25519-sha256", "ecdh-sha2-nistp256", "diffie-hellman-group14-sha256");
        String kex = expectedKex.stream().collect(Collectors.joining(","));
        String uri
                = "mina-sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}?username=admin&password=admin&keyExchangeProtocols="
                  + kex + "&knownHostsFile=" + service.getKnownHostsFile();
        template.sendBodyAndHeader(uri, "Hello World", Exchange.FILE_NAME, "hello-kex-multi.txt");

        // test setting multiple key exchange protocols works
        File file = ftpFile("hello-kex-multi.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Hello World", context.getTypeConverter().convertTo(String.class, file));

        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        assertEquals(expectedKex, endpoint.getConfiguration().getKeyExchangeProtocols());
    }

    @Test
    public void testSftpInvalidKeyExchangeProtocol() {
        String uri
                = "mina-sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}?username=admin&password=admin&keyExchangeProtocols=nonExistingKeyExchange"
                  + "&knownHostsFile=" + service.getKnownHostsFile();

        // MINA SSHD validates algorithms before connection, so we get a clear error
        CamelExecutionException exception = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Hello World", Exchange.FILE_NAME, "hello-fail.txt"));

        // The exception should contain a clear error message about the invalid protocol
        assertTrue(exception.getCause().getMessage().contains("Unknown or unsupported key exchange protocol"),
                "Should contain clear error message about invalid protocol");
        assertTrue(exception.getCause().getMessage().contains("Available protocols"),
                "Should list available protocols");
    }

}
