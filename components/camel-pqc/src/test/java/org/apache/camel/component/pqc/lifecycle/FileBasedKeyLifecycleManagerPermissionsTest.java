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
package org.apache.camel.component.pqc.lifecycle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Private keys are stored unencrypted, so leaving them at whatever the umask gives - commonly world readable under 022
 * - is not an acceptable default.
 */
@DisabledOnOs(OS.WINDOWS)
class FileBasedKeyLifecycleManagerPermissionsTest {

    @Test
    void thePrivateKeyAndItsDirectoryAreReadableOnlyByTheOwner(@TempDir Path tempDir) throws Exception {
        Path keyDir = tempDir.resolve("keys");
        FileBasedKeyLifecycleManager manager = new FileBasedKeyLifecycleManager(keyDir.toString());

        manager.storeKey("k1", keyPair(), metadata());

        Path privateKeyFile = keyDir.resolve("k1.private.json");
        assertTrue(Files.exists(privateKeyFile), "expected the private key at " + privateKeyFile);

        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(privateKeyFile));
        assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(keyDir));
    }

    /**
     * A key written by an earlier version keeps its permissions across a rewrite, because the file is truncated rather
     * than recreated.
     */
    @Test
    void aPreExistingWorldReadableKeyFileIsTightened(@TempDir Path tempDir) throws Exception {
        Path keyDir = tempDir.resolve("keys");
        FileBasedKeyLifecycleManager manager = new FileBasedKeyLifecycleManager(keyDir.toString());

        Path privateKeyFile = keyDir.resolve("k2.private.json");
        Files.writeString(privateKeyFile, "{}");
        Files.setPosixFilePermissions(privateKeyFile, PosixFilePermissions.fromString("rw-r--r--"));

        manager.storeKey("k2", keyPair(), metadata());

        Set<PosixFilePermission> actual = Files.getPosixFilePermissions(privateKeyFile);
        assertEquals(PosixFilePermissions.fromString("rw-------"), actual);
    }

    @Test
    void posixIsSupportedHere(@TempDir Path tempDir) {
        assertTrue(Files.getFileAttributeView(tempDir, PosixFileAttributeView.class) != null,
                "this test is meaningless without POSIX permissions");
    }

    private static KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static KeyMetadata metadata() {
        return new KeyMetadata("k", "RSA");
    }
}
