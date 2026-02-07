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
import org.apache.camel.component.file.remote.mina.MinaSftpConfiguration;
import org.apache.camel.component.file.remote.mina.MinaSftpEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for buffer size configuration: readBufferSize, writeBufferSize, and bulkRequests (deprecated).
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/sftp/hostkey.pem')")
public class SftpBulkRequestsIT extends SftpServerTestSupport {

    @Test
    public void testReadBufferSizeConfiguration() {
        // Test that readBufferSize is correctly parsed and applied
        Integer readBufferSize = 65536; // 64KB
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile()
                     + "&readBufferSize=" + readBufferSize;

        template.sendBodyAndHeader(uri, "Read Buffer Test", Exchange.FILE_NAME, "read-buffer-test.txt");

        File file = ftpFile("read-buffer-test.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Read Buffer Test", context.getTypeConverter().convertTo(String.class, file));

        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        assertEquals(readBufferSize, endpoint.getConfiguration().getReadBufferSize());
    }

    @Test
    public void testWriteBufferSizeConfiguration() {
        // Test that writeBufferSize is correctly parsed and applied
        Integer writeBufferSize = 98304; // 96KB
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile()
                     + "&writeBufferSize=" + writeBufferSize;

        template.sendBodyAndHeader(uri, "Write Buffer Test", Exchange.FILE_NAME, "write-buffer-test.txt");

        File file = ftpFile("write-buffer-test.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Write Buffer Test", context.getTypeConverter().convertTo(String.class, file));

        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        assertEquals(writeBufferSize, endpoint.getConfiguration().getWriteBufferSize());
    }

    @Test
    public void testBothBufferSizesConfiguration() {
        // Test that both read and write buffer sizes can be configured independently
        Integer readBufferSize = 65536;  // 64KB
        Integer writeBufferSize = 32768; // 32KB
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile()
                     + "&readBufferSize=" + readBufferSize + "&writeBufferSize=" + writeBufferSize;

        template.sendBodyAndHeader(uri, "Both Buffers Test", Exchange.FILE_NAME, "both-buffers-test.txt");

        File file = ftpFile("both-buffers-test.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Both Buffers Test", context.getTypeConverter().convertTo(String.class, file));

        MinaSftpConfiguration config = context.getEndpoint(uri, MinaSftpEndpoint.class).getConfiguration();
        assertEquals(readBufferSize, config.getReadBufferSize());
        assertEquals(writeBufferSize, config.getWriteBufferSize());
    }

    @Test
    public void testDefaultBufferSizesNull() {
        // When buffer sizes are not specified, they should be null (MINA defaults)
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile();

        template.sendBodyAndHeader(uri, "Default Buffer Test", Exchange.FILE_NAME, "default-buffer-test.txt");

        File file = ftpFile("default-buffer-test.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Default Buffer Test", context.getTypeConverter().convertTo(String.class, file));

        MinaSftpConfiguration config = context.getEndpoint(uri, MinaSftpEndpoint.class).getConfiguration();
        assertNull(config.getReadBufferSize());
        assertNull(config.getWriteBufferSize());
        assertNull(config.getBulkRequests());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testBulkRequestsDeprecatedBackwardCompatibility() {
        // Test backward compatibility: bulkRequests should still work
        Integer bulkRequests = 4;
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile()
                     + "&bulkRequests=" + bulkRequests;

        template.sendBodyAndHeader(uri, "Bulk Requests Test", Exchange.FILE_NAME, "bulk-requests-test.txt");

        File file = ftpFile("bulk-requests-test.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Bulk Requests Test", context.getTypeConverter().convertTo(String.class, file));

        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        assertEquals(bulkRequests, endpoint.getConfiguration().getBulkRequests());
    }

    @Test
    public void testBufferSizesCombinedWithBindAddress() {
        // Test combining buffer sizes with bindAddress
        Integer readBufferSize = 65536;
        Integer writeBufferSize = 65536;
        String bindAddr = "127.0.0.1";
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile()
                     + "&readBufferSize=" + readBufferSize + "&writeBufferSize=" + writeBufferSize
                     + "&bindAddress=" + bindAddr;

        template.sendBodyAndHeader(uri, "Combined Test", Exchange.FILE_NAME, "combined-test.txt");

        File file = ftpFile("combined-test.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Combined Test", context.getTypeConverter().convertTo(String.class, file));

        MinaSftpConfiguration config = context.getEndpoint(uri, MinaSftpEndpoint.class).getConfiguration();
        assertEquals(readBufferSize, config.getReadBufferSize());
        assertEquals(writeBufferSize, config.getWriteBufferSize());
        assertEquals(bindAddr, config.getBindAddress());
    }

    @Test
    public void testExplicitBufferSizeOverridesBulkRequests() {
        // Test that explicit buffer sizes take precedence over bulkRequests
        Integer bulkRequests = 4;  // Would calculate to 131072 bytes
        Integer readBufferSize = 65536;  // 64KB - should override bulkRequests
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile()
                     + "&bulkRequests=" + bulkRequests + "&readBufferSize=" + readBufferSize;

        template.sendBodyAndHeader(uri, "Override Test", Exchange.FILE_NAME, "override-test.txt");

        File file = ftpFile("override-test.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Override Test", context.getTypeConverter().convertTo(String.class, file));

        MinaSftpConfiguration config = context.getEndpoint(uri, MinaSftpEndpoint.class).getConfiguration();
        // The explicit readBufferSize should be preserved in config
        assertEquals(readBufferSize, config.getReadBufferSize());
        // bulkRequests is still set but readBufferSize takes precedence in implementation
        assertEquals(bulkRequests, config.getBulkRequests());
    }
}
