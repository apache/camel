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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * keyId values reach the file-based key store from message headers (CamelPQCKeyId / CamelPQCNewKeyId). The manager must
 * keep every key file inside its configured directory regardless of the supplied keyId.
 */
class FileBasedKeyLifecycleManagerPathTest {

    @Test
    void rejectsKeyIdWithParentTraversal(@TempDir Path tempDir) throws Exception {
        Path keyDir = tempDir.resolve("keys");
        FileBasedKeyLifecycleManager manager = new FileBasedKeyLifecycleManager(keyDir.toString());

        // A sentinel file one level above the key directory that an unconfined "../victim" keyId would target
        Path sentinel = tempDir.resolve("victim.private.json");
        Files.writeString(sentinel, "{}");

        assertThrows(IllegalArgumentException.class, () -> manager.getKey("../victim"));
        assertThrows(IllegalArgumentException.class, () -> manager.deleteKey("../victim"));

        assertTrue(Files.exists(sentinel), "a file outside the key directory must not be reachable via keyId");
    }

    @Test
    void rejectsAbsoluteSeparatorAndBlankKeyIds(@TempDir Path tempDir) throws Exception {
        Path keyDir = tempDir.resolve("keys");
        FileBasedKeyLifecycleManager manager = new FileBasedKeyLifecycleManager(keyDir.toString());

        assertThrows(IllegalArgumentException.class, () -> manager.getKeyMetadata("sub/evil"));
        assertThrows(IllegalArgumentException.class, () -> manager.getKeyMetadata("/etc/evil"));
        assertThrows(IllegalArgumentException.class,
                () -> manager.getKeyMetadata(tempDir.resolve("abs").toString()));
        assertThrows(IllegalArgumentException.class, () -> manager.getKeyMetadata(""));
    }

    @Test
    void allowsPlainKeyId(@TempDir Path tempDir) throws Exception {
        Path keyDir = tempDir.resolve("keys");
        FileBasedKeyLifecycleManager manager = new FileBasedKeyLifecycleManager(keyDir.toString());

        // A normal flat keyId is accepted: metadata for an absent key returns null rather than being rejected
        assertNull(manager.getKeyMetadata("tenant-a-signing-key"));
    }
}
