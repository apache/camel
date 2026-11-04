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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * XMSS, XMSSMT, LMS and HSS are one-time-signature schemes, so a key directory that two processes read from must not
 * hand the same key to both of them.
 */
public class FileBasedKeyLifecycleManagerStatefulClaimTest {

    private static final String KEY_ID = "lms-key";

    @Test
    void aClaimIsGrantedAndTakesALockFile(@TempDir Path tempDir) throws Exception {
        Path keyDir = tempDir.resolve("keys");
        FileBasedKeyLifecycleManager manager = new FileBasedKeyLifecycleManager(keyDir.toString());

        assertTrue(manager.claimStatefulKey(KEY_ID), "the claim must be granted");
        assertTrue(Files.exists(keyDir.resolve(KEY_ID + ".lock")), "the claim must be visible to other processes");

        // claiming what this manager already holds is not contention
        assertTrue(manager.claimStatefulKey(KEY_ID), "re-claiming the same key must succeed");

        manager.releaseStatefulKey(KEY_ID);
    }

    @Test
    void aSecondHolderOfTheSameKeyDirectoryIsRefused(@TempDir Path tempDir) throws Exception {
        Path keyDir = tempDir.resolve("keys");
        FileBasedKeyLifecycleManager first = new FileBasedKeyLifecycleManager(keyDir.toString());
        FileBasedKeyLifecycleManager second = new FileBasedKeyLifecycleManager(keyDir.toString());

        assertTrue(first.claimStatefulKey(KEY_ID));

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> second.claimStatefulKey(KEY_ID));
        assertTrue(e.getMessage().contains(KEY_ID), "the message must name the key: " + e.getMessage());

        // The claim is still registered after the refusal. Note this only exercises the JVM's own lock
        // bookkeeping: a third holder in this process is refused by OverlappingFileLockException before the OS
        // lock is consulted at all, so it cannot tell whether the underlying lock survived. Whether closing a
        // second descriptor drops the OS lock is a cross-process question and needs a second JVM to answer.
        FileBasedKeyLifecycleManager third = new FileBasedKeyLifecycleManager(keyDir.toString());
        assertThrows(IllegalStateException.class, () -> third.claimStatefulKey(KEY_ID),
                "the claim must still be registered after a second holder was refused");

        first.releaseStatefulKey(KEY_ID);
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
