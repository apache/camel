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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class SftpSimpleProduceDisconnectIT extends SftpServerTestSupport {

    @Test
    public void testSftpSimpleProduce() throws Exception {
        template.sendBodyAndHeader(
                "sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}?username=admin&password=admin&knownHostsFile="
                                   + service.getKnownHostsFile(),
                "Hello World", Exchange.FILE_NAME, "hello.txt");

        File file = ftpFile("hello.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Hello World", context.getTypeConverter().convertTo(String.class, file));

        // Restart the SFTP server. Wait until the new server is accepting connections before the
        // second upload. This also gives JSch enough time to process the EOF/RST from the old
        // server's connection, so that isConnected() returns false, isRecentlyUsed() returns false,
        // and the producer sends a noop that detects the stale session and reconnects.
        service.tearDownServer();
        service.setUpServer();
        int port = service.getPort();
        await().atMost(10, TimeUnit.SECONDS).pollInterval(50, TimeUnit.MILLISECONDS)
                .until(() -> {
                    try (Socket s = new Socket()) {
                        s.connect(new InetSocketAddress("localhost", port), 100);
                        return true;
                    } catch (IOException e) {
                        return false;
                    }
                });

        template.sendBodyAndHeader(
                "sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}?username=admin&password=admin&knownHostsFile="
                                   + service.getKnownHostsFile(),
                "Hello World", Exchange.FILE_NAME, "hello1.txt");

        file = ftpFile("hello1.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Hello World", context.getTypeConverter().convertTo(String.class, file));
    }
}
