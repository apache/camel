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
import java.security.KeyPairGenerator;
import java.security.PublicKey;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * XMSS, XMSSMT, LMS and HSS are one-time-signature schemes, so a key directory that two processes read from must not
 * hand the same key to both of them. The claim is on the key material: the lock file is named from the fingerprint of
 * the public key, not from the name the key is stored under.
 */
public class FileBasedKeyLifecycleManagerStatefulClaimTest {

    private static final String KEY_ID = "lms-key";

    private static PublicKey key;
    private static PublicKey otherKey;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }
    }

    @BeforeAll
    static void generateKeys() throws Exception {
        // the claim only fingerprints the public key, so any key with an encoding will do, and these are quick
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        key = generator.generateKeyPair().getPublic();
        otherKey = generator.generateKeyPair().getPublic();
    }

    private static Path lockFile(Path keyDir, PublicKey publicKey) {
        return keyDir.resolve(KeyLifecycleManager.fingerprint(publicKey) + ".lock");
    }

    @Test
    void aClaimIsGrantedAndTakesALockFile(@TempDir Path tempDir) throws Exception {
        Path keyDir = tempDir.resolve("keys");
        FileBasedKeyLifecycleManager manager = new FileBasedKeyLifecycleManager(keyDir.toString());

        assertTrue(manager.claimStatefulKey(KEY_ID, key), "the claim must be granted");
        assertTrue(Files.exists(lockFile(keyDir, key)), "the claim must be visible to other processes");
        assertFalse(Files.exists(keyDir.resolve(KEY_ID + ".lock")), "the lock is on the key, not on its name");

        // claiming what this manager already holds is not contention
        assertTrue(manager.claimStatefulKey(KEY_ID, key), "re-claiming the same key must succeed");

        // each claim is a holder: one release keeps the key, the second gives it back
        manager.releaseStatefulKey(KEY_ID, key);
        FileBasedKeyLifecycleManager other = new FileBasedKeyLifecycleManager(keyDir.toString());
        assertThrows(IllegalStateException.class, () -> other.claimStatefulKey(KEY_ID, key),
                "the key must stay claimed while a holder remains");
        manager.releaseStatefulKey(KEY_ID, key);
        assertDoesNotThrow(() -> other.claimStatefulKey(KEY_ID, key), "the last release must give the key back");
        other.releaseStatefulKey(KEY_ID, key);
    }

    @Test
    void aSecondHolderOfTheSameKeyDirectoryIsRefused(@TempDir Path tempDir) throws Exception {
        Path keyDir = tempDir.resolve("keys");
        FileBasedKeyLifecycleManager first = new FileBasedKeyLifecycleManager(keyDir.toString());
        FileBasedKeyLifecycleManager second = new FileBasedKeyLifecycleManager(keyDir.toString());

        assertTrue(first.claimStatefulKey(KEY_ID, key));

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> second.claimStatefulKey(KEY_ID, key));
        assertTrue(e.getMessage().contains(KEY_ID), "the message must name the key: " + e.getMessage());

        // The refusal comes from the registry of lock files this JVM holds, before a second channel is opened on
        // the file, so there is no channel left behind for the garbage collector to close - which would drop the
        // first holder's lock. The claim is still registered after the refusal.
        FileBasedKeyLifecycleManager third = new FileBasedKeyLifecycleManager(keyDir.toString());
        assertThrows(IllegalStateException.class, () -> third.claimStatefulKey(KEY_ID, key),
                "the claim must still be registered after a second holder was refused");

        first.releaseStatefulKey(KEY_ID, key);
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

        assertTrue(first.claimStatefulKey(KEY_ID, key));
        // the registry holds the real path of the lock file, so the link does not pass for another file
        assertThrows(IllegalStateException.class, () -> second.claimStatefulKey(KEY_ID, key),
                "the same directory under another path must be refused");

        first.releaseStatefulKey(KEY_ID, key);
    }

    @Test
    void theClaimFollowsTheKeyNotItsName(@TempDir Path tempDir) throws Exception {
        Path keyDir = tempDir.resolve("keys");
        FileBasedKeyLifecycleManager first = new FileBasedKeyLifecycleManager(keyDir.toString());
        FileBasedKeyLifecycleManager second = new FileBasedKeyLifecycleManager(keyDir.toString());

        assertTrue(first.claimStatefulKey("one-name", key));

        // the same key under another name is the same key: one of them would hand out the other's indices
        assertThrows(IllegalStateException.class, () -> second.claimStatefulKey("another-name", key),
                "the same key material under another name must be refused");
        assertTrue(Files.exists(lockFile(keyDir, key)));
        assertFalse(Files.exists(keyDir.resolve("one-name.lock")));
        assertFalse(Files.exists(keyDir.resolve("another-name.lock")));

        first.releaseStatefulKey("one-name", key);
    }

    @Test
    void differentKeysUnderOneNameDoNotContend(@TempDir Path tempDir) throws Exception {
        Path keyDir = tempDir.resolve("keys");
        FileBasedKeyLifecycleManager first = new FileBasedKeyLifecycleManager(keyDir.toString());
        FileBasedKeyLifecycleManager second = new FileBasedKeyLifecycleManager(keyDir.toString());

        assertTrue(first.claimStatefulKey(KEY_ID, key));
        // another key that happens to carry the same name is another key, with its own one-time indices
        assertTrue(second.claimStatefulKey(KEY_ID, otherKey), "a different key under the same name is not contention");
        assertTrue(Files.exists(lockFile(keyDir, key)));
        assertTrue(Files.exists(lockFile(keyDir, otherKey)));

        first.releaseStatefulKey(KEY_ID, key);
        second.releaseStatefulKey(KEY_ID, otherKey);
    }

    @Test
    void anUnnamedKeyIsClaimed(@TempDir Path tempDir) throws Exception {
        Path keyDir = tempDir.resolve("keys");
        FileBasedKeyLifecycleManager first = new FileBasedKeyLifecycleManager(keyDir.toString());
        FileBasedKeyLifecycleManager second = new FileBasedKeyLifecycleManager(keyDir.toString());

        // a key the exchange gives no name to is still one key material, so it has something to lock
        assertTrue(first.claimStatefulKey(null, key), "a key without a name must be claimed by its fingerprint");
        assertTrue(Files.exists(lockFile(keyDir, key)));

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> second.claimStatefulKey(null, key));
        assertTrue(e.getMessage().contains("fingerprint"), "the message must identify the key: " + e.getMessage());

        first.releaseStatefulKey(null, key);
        assertDoesNotThrow(() -> second.claimStatefulKey(null, key));
        second.releaseStatefulKey(null, key);
    }

    @Test
    void aKeyWithoutAnEncodingIsClaimedByItsName(@TempDir Path tempDir) throws Exception {
        Path keyDir = tempDir.resolve("keys");
        FileBasedKeyLifecycleManager manager = new FileBasedKeyLifecycleManager(keyDir.toString());
        PublicKey opaque = new OpaquePublicKey();

        assertTrue(manager.claimStatefulKey("named", opaque), "a key with no encoding falls back to its name");
        assertTrue(Files.exists(keyDir.resolve("named.lock")));
        manager.releaseStatefulKey("named", opaque);

        // with neither an encoding nor a name there is nothing to claim
        assertThrows(IllegalArgumentException.class, () -> manager.claimStatefulKey(null, opaque));
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
                    return manager.claimStatefulKey(KEY_ID, key);
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

        // each thread claimed once, so the key is held eight times, and another manager is still refused
        FileBasedKeyLifecycleManager other = new FileBasedKeyLifecycleManager(keyDir.toString());
        assertThrows(IllegalStateException.class, () -> other.claimStatefulKey(KEY_ID, key));

        // every holder releases the key, and only then is it given back
        for (int i = 0; i < threads; i++) {
            manager.releaseStatefulKey(KEY_ID, key);
        }
        assertDoesNotThrow(() -> other.claimStatefulKey(KEY_ID, key),
                "once every holder has released the key, another manager must be able to claim it");
        other.releaseStatefulKey(KEY_ID, key);
    }

    @Test
    void aReleasedKeyCanBeClaimedAgain(@TempDir Path tempDir) throws Exception {
        Path keyDir = tempDir.resolve("keys");
        FileBasedKeyLifecycleManager first = new FileBasedKeyLifecycleManager(keyDir.toString());
        FileBasedKeyLifecycleManager second = new FileBasedKeyLifecycleManager(keyDir.toString());

        assertTrue(first.claimStatefulKey(KEY_ID, key));
        first.releaseStatefulKey(KEY_ID, key);

        assertDoesNotThrow(() -> second.claimStatefulKey(KEY_ID, key),
                "a key given back must be available to the next owner");
        second.releaseStatefulKey(KEY_ID, key);
    }

    @Test
    void releasingAKeyThatWasNeverClaimedDoesNothing(@TempDir Path tempDir) throws Exception {
        FileBasedKeyLifecycleManager manager = new FileBasedKeyLifecycleManager(tempDir.resolve("keys").toString());

        assertDoesNotThrow(() -> manager.releaseStatefulKey(KEY_ID, key));
    }

    @Test
    void aNameThatEscapesTheDirectoryIsRejectedWhenItNamesTheLockFile(@TempDir Path tempDir) throws Exception {
        Path keyDir = tempDir.resolve("keys");
        FileBasedKeyLifecycleManager manager = new FileBasedKeyLifecycleManager(keyDir.toString());

        // the name names the lock file only for a key without an encoding, and is then confined like every other
        // file this manager writes from a header-supplied keyId
        assertThrows(IllegalArgumentException.class, () -> manager.claimStatefulKey("../escaped", new OpaquePublicKey()));

        // for a key with an encoding the lock file is named from the fingerprint, so the name cannot escape
        assertTrue(manager.claimStatefulKey("../escaped", key));
        assertTrue(Files.exists(lockFile(keyDir, key)));
        assertFalse(Files.exists(tempDir.resolve("escaped.lock")));
        manager.releaseStatefulKey("../escaped", key);
    }

    @Test
    void aNewClaimReadsTheKeyAndItsMetadataFromTheStore(@TempDir Path tempDir) throws Exception {
        Path keyDir = tempDir.resolve("keys");
        FileBasedKeyLifecycleManager leaving = new FileBasedKeyLifecycleManager(keyDir.toString());
        FileBasedKeyLifecycleManager arriving = new FileBasedKeyLifecycleManager(keyDir.toString());

        KeyPair atStartup = leaving.generateKeyPair("LMS", KEY_ID);
        assertTrue(leaving.claimStatefulKey(KEY_ID, atStartup.getPublic()));
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
        leaving.releaseStatefulKey(KEY_ID, atStartup.getPublic());

        // the claim is what makes the arriving instance read the store again
        assertTrue(arriving.claimStatefulKey(KEY_ID, atStartup.getPublic()));
        assertArrayEquals(advanced.getPublic().getEncoded(), arriving.getKey(KEY_ID).getPublic().getEncoded(),
                "a new claim must read the key from the store, not from the cache");
        assertEquals(42, arriving.getKeyMetadata(KEY_ID).getUsageCount(),
                "a new claim must read the metadata from the store, not from the cache");
        arriving.releaseStatefulKey(KEY_ID, atStartup.getPublic());
    }

    /**
     * A public key that gives no encoding, as a hardware-backed or otherwise opaque key may.
     */
    private static final class OpaquePublicKey implements PublicKey {

        @Override
        public String getAlgorithm() {
            return "opaque";
        }

        @Override
        public String getFormat() {
            return null;
        }

        @Override
        public byte[] getEncoded() {
            return null;
        }
    }
}
