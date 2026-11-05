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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * XMSS, XMSSMT, LMS and HSS are one-time-signature schemes, so a key directory that two processes read from must not
 * hand the same key to both of them.
 */
public class FileBasedKeyLifecycleManagerStatefulClaimTest {

    private static final String KEY_ID = "lms-key";

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }
    }

    @Test
    void aClaimIsGrantedAndTakesALockFile(@TempDir Path tempDir) throws Exception {
        Path keyDir = tempDir.resolve("keys");
        FileBasedKeyLifecycleManager manager = new FileBasedKeyLifecycleManager(keyDir.toString());

        assertTrue(manager.claimStatefulKey(KEY_ID), "the claim must be granted");
        assertTrue(Files.exists(keyDir.resolve(KEY_ID + ".lock")), "the claim must be visible to other processes");

        // claiming what this manager already holds is not contention
        assertTrue(manager.claimStatefulKey(KEY_ID), "re-claiming the same key must succeed");

        // each claim is a holder: one release keeps the key, the second gives it back
        manager.releaseStatefulKey(KEY_ID);
        FileBasedKeyLifecycleManager other = new FileBasedKeyLifecycleManager(keyDir.toString());
        assertThrows(IllegalStateException.class, () -> other.claimStatefulKey(KEY_ID),
                "the key must stay claimed while a holder remains");
        manager.releaseStatefulKey(KEY_ID);
        assertDoesNotThrow(() -> other.claimStatefulKey(KEY_ID), "the last release must give the key back");
        other.releaseStatefulKey(KEY_ID);
    }

    @Test
    void aSecondHolderOfTheSameKeyDirectoryIsRefused(@TempDir Path tempDir) throws Exception {
        Path keyDir = tempDir.resolve("keys");
        FileBasedKeyLifecycleManager first = new FileBasedKeyLifecycleManager(keyDir.toString());
        FileBasedKeyLifecycleManager second = new FileBasedKeyLifecycleManager(keyDir.toString());

        assertTrue(first.claimStatefulKey(KEY_ID));

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> second.claimStatefulKey(KEY_ID));
        assertTrue(e.getMessage().contains(KEY_ID), "the message must name the key: " + e.getMessage());

        // The refusal comes from the registry of lock files this JVM holds, before a second channel is opened on
        // the file, so there is no channel left behind for the garbage collector to close - which would drop the
        // first holder's lock. The claim is still registered after the refusal.
        FileBasedKeyLifecycleManager third = new FileBasedKeyLifecycleManager(keyDir.toString());
        assertThrows(IllegalStateException.class, () -> third.claimStatefulKey(KEY_ID),
                "the claim must still be registered after a second holder was refused");

        first.releaseStatefulKey(KEY_ID);
    }

    @Test
    void aSecondHolderThroughAnotherPathToTheSameDirectoryIsRefused(@TempDir Path tempDir) throws Exception {
        Path keyDir = tempDir.resolve("keys");
        FileBasedKeyLifecycleManager first = new FileBasedKeyLifecycleManager(keyDir.toString());
        Path link = tempDir.resolve("keys-link");
        try {
            Files.createSymbolicLink(link, keyDir);
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.abort("symbolic links are not available here: " + e);
        }
        FileBasedKeyLifecycleManager second = new FileBasedKeyLifecycleManager(link.toString());

        assertTrue(first.claimStatefulKey(KEY_ID));
        // the registry holds the real path of the lock file, so the link does not pass for another file
        assertThrows(IllegalStateException.class, () -> second.claimStatefulKey(KEY_ID),
                "the same directory under another path must be refused");

        first.releaseStatefulKey(KEY_ID);
    }

    @Test
    void aNewClaimReadsTheKeyAndItsMetadataFromTheStore(@TempDir Path tempDir) throws Exception {
        Path keyDir = tempDir.resolve("keys");
        FileBasedKeyLifecycleManager leaving = new FileBasedKeyLifecycleManager(keyDir.toString());
        FileBasedKeyLifecycleManager arriving = new FileBasedKeyLifecycleManager(keyDir.toString());

        KeyPair atStartup = leaving.generateKeyPair("LMS", KEY_ID);
        assertTrue(leaving.claimStatefulKey(KEY_ID));
        // the arriving instance loads the key and its metadata, and caches both, while it waits for the claim
        assertArrayEquals(atStartup.getPublic().getEncoded(), arriving.getKey(KEY_ID).getPublic().getEncoded());
        assertEquals(0, arriving.getKeyMetadata(KEY_ID).getUsageCount());

        // the leaving instance moves the key on while it holds the claim
        KeyPair advanced = leaving.generateKeyPair("LMS", KEY_ID);
        KeyMetadata used = leaving.getKeyMetadata(KEY_ID);
        used.setUsageCount(42);
        leaving.updateKeyMetadata(KEY_ID, used);
        assertArrayEquals(atStartup.getPublic().getEncoded(), arriving.getKey(KEY_ID).getPublic().getEncoded(),
                "before the claim the arriving instance still serves what it loaded");
        leaving.releaseStatefulKey(KEY_ID);

        // the claim is what makes the arriving instance read the store again
        assertTrue(arriving.claimStatefulKey(KEY_ID));
        assertArrayEquals(advanced.getPublic().getEncoded(), arriving.getKey(KEY_ID).getPublic().getEncoded(),
                "a new claim must read the key from the store, not from the cache");
        assertEquals(42, arriving.getKeyMetadata(KEY_ID).getUsageCount(),
                "a new claim must read the metadata from the store, not from the cache");
        arriving.releaseStatefulKey(KEY_ID);
    }

    @Test
    void concurrentFirstClaimsOnOneManagerAllSucceed(@TempDir Path tempDir) throws Exception {
        Path keyDir = tempDir.resolve("keys");
        FileBasedKeyLifecycleManager manager = new FileBasedKeyLifecycleManager(keyDir.toString());

        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return manager.claimStatefulKey(KEY_ID);
                }));
            }
            start.countDown();

            for (Future<Boolean> r : results) {
                // Every caller sharing the manager must get the claim. Before the claim was made atomic, the losers
                // of the race opened a second channel, hit OverlappingFileLockException and were told to "share the
                // KeyLifecycleManager instance" - which they already were.
                assertTrue(assertDoesNotThrow(() -> r.get(20, TimeUnit.SECONDS)),
                        "a concurrent claim on the same manager must succeed");
            }
        } finally {
            pool.shutdownNow();
        }

        // and the key is held exactly once, so another holder is still refused
        FileBasedKeyLifecycleManager other = new FileBasedKeyLifecycleManager(keyDir.toString());
        assertThrows(IllegalStateException.class, () -> other.claimStatefulKey(KEY_ID));

        manager.releaseStatefulKey(KEY_ID);
    }

    @Test
    void aReleasedKeyCanBeClaimedAgain(@TempDir Path tempDir) throws Exception {
        Path keyDir = tempDir.resolve("keys");
        FileBasedKeyLifecycleManager first = new FileBasedKeyLifecycleManager(keyDir.toString());
        FileBasedKeyLifecycleManager second = new FileBasedKeyLifecycleManager(keyDir.toString());

        assertTrue(first.claimStatefulKey(KEY_ID));
        first.releaseStatefulKey(KEY_ID);

        assertDoesNotThrow(() -> second.claimStatefulKey(KEY_ID),
                "a key given back must be available to the next owner");
        second.releaseStatefulKey(KEY_ID);
    }

    @Test
    void releasingAKeyThatWasNeverClaimedDoesNothing(@TempDir Path tempDir) throws Exception {
        FileBasedKeyLifecycleManager manager = new FileBasedKeyLifecycleManager(tempDir.resolve("keys").toString());

        assertDoesNotThrow(() -> manager.releaseStatefulKey(KEY_ID));
    }

    @Test
    void aKeyIdThatEscapesTheDirectoryIsRejected(@TempDir Path tempDir) throws Exception {
        FileBasedKeyLifecycleManager manager = new FileBasedKeyLifecycleManager(tempDir.resolve("keys").toString());

        // the claim resolves a file name from a header-supplied keyId, as every other file this manager writes does
        assertThrows(IllegalArgumentException.class, () -> manager.claimStatefulKey("../escaped"));
    }
}
