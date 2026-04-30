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
package org.apache.camel.component.smb;

import java.net.URI;
import java.util.EnumSet;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.mssmb2.SMBApiException;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.smb.services.SmbService;
import org.apache.camel.test.infra.smb.services.SmbServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that STATUS_ACCESS_DENIED (0xc0000022) is thrown when attempting to delete a file on a share where the
 * authenticated user lacks delete permissions. This reproduces the scenario reported by customers using NetApp ONTAP
 * SMB shares where read/write is allowed but delete is denied.
 *
 * Two shares simulate the ONTAP behavior:
 * <ul>
 * <li>{@code data-no-delete} - sticky bit on directory, root-owned files (POSIX-level denial)</li>
 * <li>{@code data-unix-security} - {@code nt acl support = no} simulates ONTAP UNIX security style where NTFS ACLs are
 * ignored and only POSIX permissions are evaluated</li>
 * </ul>
 */
public class SmbDeleteAccessDeniedIT extends CamelTestSupport {

    private static final String NO_DELETE_SHARE = "data-no-delete";
    private static final String UNIX_SECURITY_SHARE = "data-unix-security";
    private static final String FIXED_DELETE_SHARE = "data-fixed-delete";
    private static final String UNIX_OWNED_SHARE = "data-unix-owned";

    @RegisterExtension
    public static SmbService service = SmbServiceFactory.createSingletonService();

    @Test
    public void testDeleteAccessDeniedViaSmbOperations() throws Exception {
        SmbConfiguration config = createConfig(NO_DELETE_SHARE);
        SmbOperations operations = new SmbOperations(config);

        try {
            operations.connect(config, null);

            SMBApiException thrown = assertThrows(SMBApiException.class,
                    () -> operations.deleteFile("1.txt"));

            assertEquals(0xc0000022L, thrown.getStatusCode(),
                    "Expected STATUS_ACCESS_DENIED (0xc0000022) but got: "
                                                              + String.format("0x%08x", thrown.getStatusCode()));
        } finally {
            operations.disconnect();
        }
    }

    @Test
    public void testDeleteAccessDeniedOnUnixSecurityStyleViaSmbOperations() throws Exception {
        SmbConfiguration config = createConfig(UNIX_SECURITY_SHARE);
        SmbOperations operations = new SmbOperations(config);

        try {
            operations.connect(config, null);

            SMBApiException thrown = assertThrows(SMBApiException.class,
                    () -> operations.deleteFile("1.txt"));

            assertEquals(0xc0000022L, thrown.getStatusCode(),
                    "Expected STATUS_ACCESS_DENIED (0xc0000022) but got: "
                                                              + String.format("0x%08x", thrown.getStatusCode()));
        } finally {
            operations.disconnect();
        }
    }

    @Test
    public void testReadStillWorksOnNoDeleteShare() throws Exception {
        int port = Integer.parseInt(service.address().split(":")[1]);

        try (SMBClient smbClient = new SMBClient();
             Connection connection = smbClient.connect("localhost", port)) {

            AuthenticationContext ac
                    = new AuthenticationContext(service.userName(), service.password().toCharArray(), null);
            Session session = connection.authenticate(ac);

            try (DiskShare share = (DiskShare) session.connectShare(NO_DELETE_SHARE)) {
                try (File f = share.openFile("1.txt", EnumSet.of(AccessMask.GENERIC_READ), null,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OPEN, null)) {
                    byte[] content = f.getInputStream().readAllBytes();
                    assertTrue(content.length > 0, "Should be able to read file content");
                }
            }
        }
    }

    @Test
    public void testDeleteSucceedsOnUnixStyleWhenUserOwnsFiles() throws Exception {
        SmbConfiguration config = createConfig(UNIX_OWNED_SHARE);
        SmbOperations operations = new SmbOperations(config);

        try {
            operations.connect(config, null);

            assertTrue(operations.existsFile("1.txt"), "File 1.txt should exist before delete");
            assertTrue(operations.deleteFile("1.txt"),
                    "Delete should succeed on UNIX security style when user owns the file, regardless of ACLs");
        } finally {
            operations.disconnect();
        }
    }

    @Test
    public void testDeleteSucceedsWhenUserOwnsFiles() throws Exception {
        SmbConfiguration config = createConfig(FIXED_DELETE_SHARE);
        SmbOperations operations = new SmbOperations(config);

        try {
            operations.connect(config, null);

            assertTrue(operations.existsFile("1.txt"), "File 1.txt should exist before delete");
            assertTrue(operations.deleteFile("1.txt"), "Delete should succeed when user owns the file");
        } finally {
            operations.disconnect();
        }
    }

    private SmbConfiguration createConfig(String shareName) {
        String host = service.hostname();
        int port = service.port();
        URI uri = URI.create(String.format("smb://%s:%d/%s", host, port, shareName));
        SmbConfiguration config = new SmbConfiguration(uri);
        config.setUsername(service.userName());
        config.setPassword(service.password());
        return config;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
            }
        };
    }
}
