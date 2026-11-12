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
package org.apache.camel.processor.aggregate.hazelcast;

import java.util.Set;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.transaction.TransactionContext;
import com.hazelcast.transaction.TransactionException;
import com.hazelcast.transaction.TransactionOptions;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.OptimisticLockingAggregationRepository.OptimisticLockingException;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;

import static org.apache.camel.test.junit6.TestSupport.assertCollectionSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

public class HazelcastAggregationRepositoryOperationsTest extends HazelcastAggregationRepositoryCamelTestSupport {

    private static final String THREAD_SAFE_REPO = "threadSafeRepo";
    private static final String OPTIMISTIC_REPO = "optimisticRepo";

    @Test
    public void checkOptimisticAddOfNewExchange() throws Exception {
        HazelcastAggregationRepository repoOne = new HazelcastAggregationRepository(OPTIMISTIC_REPO, true, getFirstInstance());
        HazelcastAggregationRepository repoTwo = new HazelcastAggregationRepository(OPTIMISTIC_REPO, true, getSecondInstance());

        try {
            repoOne.doStart();
            repoTwo.doStart();

            final String testBody = "This is an optimistic test body. Sincerely yours, Captain Obvious.";
            final String key = "optimisticKey";
            Exchange newEx = createExchangeWithBody(testBody);
            Exchange oldEx = repoOne.add(context(), key, null, newEx);

            assertNull(oldEx, "Old exchange should be null.");

            final String theNewestBody = "This is the newest test body.";
            Exchange theNewestEx = createExchangeWithBody(theNewestBody);

            oldEx = repoTwo.add(context(), key, newEx, theNewestEx);
            assertNotNull(oldEx, "oldEx is null");
            assertEquals(newEx.getIn().getBody(), oldEx.getIn().getBody());

        } finally {
            repoOne.stop();
            repoTwo.stop();
        }
    }

    @Test
    public void checkThreadSafeAddOfNewExchange() throws Exception {
        HazelcastAggregationRepository repoOne
                = new HazelcastAggregationRepository(THREAD_SAFE_REPO, false, getFirstInstance());
        HazelcastAggregationRepository repoTwo
                = new HazelcastAggregationRepository(THREAD_SAFE_REPO, false, getSecondInstance());

        try {
            repoOne.doStart();
            repoTwo.doStart();

            final String testBody = "This is a thread-safe test body. Sincerely yours, Captain Obvious.";
            final String key = "threadSafeKey";
            Exchange newEx = createExchangeWithBody(testBody);
            Exchange oldEx = repoOne.add(context(), key, newEx);

            assertNull(oldEx, "Old exchange should be null.");

            final String theNewestBody = "This is the newest test body.";
            Exchange theNewestEx = createExchangeWithBody(theNewestBody);

            oldEx = repoTwo.add(context(), key, theNewestEx);
            assertNotNull(oldEx, "oldEx is null");
            assertEquals(newEx.getIn().getBody(), oldEx.getIn().getBody());

        } finally {
            repoOne.stop();
            repoTwo.stop();
        }
    }

    @Test
    public void checkOptimisticGet() throws Exception {
        HazelcastAggregationRepository repoOne = new HazelcastAggregationRepository(THREAD_SAFE_REPO, true, getFirstInstance());
        HazelcastAggregationRepository repoTwo
                = new HazelcastAggregationRepository(THREAD_SAFE_REPO, true, getSecondInstance());
        try {
            repoOne.start();
            repoTwo.start();

            final String testBody = "This is an optimistic test body. Sincerely yours, Captain Obvious.";
            final String key = "optimisticGetKey";

            Exchange ex = createExchangeWithBody(testBody);
            repoOne.add(context(), key, null, ex);

            assertEquals(1, getFirstInstance().getMap(THREAD_SAFE_REPO).size());
            assertEquals(1, getSecondInstance().getMap(THREAD_SAFE_REPO).size());

            Exchange gotEx = repoTwo.get(context(), key);
            assertNotNull(gotEx, "gotEx is null");
            assertEquals(gotEx.getIn().getBody(), ex.getIn().getBody(), "ex and gotEx should be equal");
        } finally {
            repoOne.doStop();
            repoTwo.doStop();
        }
    }

    @Test
    public void checkThreadSafeGet() throws Exception {
        HazelcastAggregationRepository repoOne = new HazelcastAggregationRepository(OPTIMISTIC_REPO, false, getFirstInstance());
        HazelcastAggregationRepository repoTwo
                = new HazelcastAggregationRepository(OPTIMISTIC_REPO, false, getSecondInstance());

        try {
            repoOne.start();
            repoTwo.start();

            final String testBody = "This is a thread-safe test body. Sincerely yours, Captain Obvious.";
            final String key = "threadSafeKey";

            Exchange ex = createExchangeWithBody(testBody);
            repoOne.add(context(), key, ex);

            Exchange gotEx = repoTwo.get(context(), key);
            assertNotNull(gotEx, "gotEx is null");
            assertEquals(gotEx.getIn().getBody(), ex.getIn().getBody(), "ex and gotEx should be equal");
        } finally {
            repoOne.doStop();
            repoTwo.doStop();
        }
    }

    @Test
    public void checkOptimisticPersistentRemove() throws Exception {
        final String persistentRepoName = String.format("%s-completed", OPTIMISTIC_REPO);
        HazelcastAggregationRepository repoOne
                = new HazelcastAggregationRepository(OPTIMISTIC_REPO, persistentRepoName, true, getFirstInstance());
        HazelcastAggregationRepository repoTwo
                = new HazelcastAggregationRepository(OPTIMISTIC_REPO, persistentRepoName, true, getSecondInstance());

        try {
            repoOne.doStart();
            repoTwo.doStart();

            final String testBody = "This is an optimistic test body. Sincerely yours, Captain Obvious.";
            final String key = "optimisticPersistentRemoveKey";

            Exchange ex = createExchangeWithBody(testBody);

            repoOne.add(context(), key, null, ex);

            Exchange getBackEx = repoTwo.get(context(), key);
            assertNotNull(getBackEx, "getBackEx should not be null.");

            repoTwo.remove(context(), key, ex);

            getBackEx = repoOne.get(context(), key);
            assertNull(getBackEx, "getBackEx should be null here.");

            Set<String> keys = repoTwo.scan(context());
            assertCollectionSize(keys, 1);

            getBackEx = repoOne.recover(context(), keys.iterator().next());
            assertNotNull(getBackEx, "getBackEx got from persistent repo should not be null.");

        } finally {
            repoOne.doStop();
            repoTwo.doStop();
        }
    }

    @Test
    public void checkOptimisticRemoveOfAStaleExchangeLeavesNothingToRecover() throws Exception {
        HazelcastAggregationRepository repo
                = new HazelcastAggregationRepository("optimisticRemoveStaleRepo", true, getFirstInstance());

        try {
            repo.doStart();

            final String key = "optimisticRemoveStaleKey";
            Exchange ex = createExchangeWithBody("The current group.");
            repo.add(context(), key, null, ex);
            Exchange staleEx = createExchangeWithBody("A stale group.");

            assertThrows(OptimisticLockingException.class, () -> repo.remove(context(), key, staleEx));

            assertTrue(repo.scan(context()).isEmpty(), "a failed remove must not leave an exchange to recover");
            assertEquals(ex.getIn().getBody(), repo.get(context(), key).getIn().getBody());
        } finally {
            repo.doStop();
        }
    }

    /**
     * Regression test for CAMEL-26663: when the transaction that atomically removes the entry from the operational
     * cache and writes the recovery entry fails to commit, the group must still be present in the operational map so it
     * can be retried — it must not be silently lost.
     * <p>
     * This test drives the production {@code remove()} via a Mockito delegate that intercepts
     * {@link HazelcastInstance#newTransactionContext} and makes {@code commitTransaction()} throw,
     * so that if the production code were changed back to a plain remove-then-put, this test would still catch the
     * regression.
     */
    @Test
    public void checkOptimisticRemoveKeepsTheGroupWhenTheTransactionFails() throws Exception {
        final String persistentRepoName = "optimisticRemoveTxFailsRepo-completed";

        // Add the group using the real instance
        HazelcastAggregationRepository realRepo
                = new HazelcastAggregationRepository(
                        "optimisticRemoveTxFailsRepo", persistentRepoName, true, getFirstInstance());
        realRepo.doStart();
        final String key = "optimisticRemoveTxFailsKey";
        Exchange ex = createExchangeWithBody("This group must not be lost.");
        realRepo.add(context(), key, null, ex);
        realRepo.doStop();

        // Wrap the real instance with a Mockito delegate that makes commitTransaction() throw
        HazelcastInstance real = getFirstInstance();
        HazelcastInstance hz = mock(HazelcastInstance.class, AdditionalAnswers.delegatesTo(real));
        doAnswer(inv -> {
            TransactionContext ctx = mock(TransactionContext.class,
                    AdditionalAnswers.delegatesTo(real.newTransactionContext(inv.getArgument(0))));
            doThrow(new TransactionException("Simulated commit failure"))
                    .when(ctx).commitTransaction();
            return ctx;
        }).when(hz).newTransactionContext(any(TransactionOptions.class));

        // Drive the production remove() — the commit will fail, triggering rollback and RuntimeCamelException
        HazelcastAggregationRepository repo
                = new HazelcastAggregationRepository("optimisticRemoveTxFailsRepo", persistentRepoName, true, hz);
        try {
            repo.doStart();
            assertThrows(RuntimeCamelException.class, () -> repo.remove(context(), key, ex));

            // The group must still be in the operational map — the rollback restored it
            Exchange getBackEx = realRepo.get(context(), key);
            assertNotNull(getBackEx, "the group must still be in the repository after a failed transaction");
            assertEquals(ex.getIn().getBody(), getBackEx.getIn().getBody());

            // No recovery entry must have been written — the transaction was rolled back before the put
            assertTrue(realRepo.scan(context()).isEmpty(),
                    "no recovery entry must have been written after a rolled-back transaction");
        } finally {
            repo.doStop();
        }
    }

    /**
     * Regression test for CAMEL-26796: in the optimistic remove() path, two threads racing on the same group both hold
     * the same exchange (same exchangeId). The losing thread must not corrupt the winning thread's recovery entry.
     * <p>
     * Before the fix, the cleanup call on the losing thread would delete the winning thread's recovery entry (they both
     * had the same exchangeId), making the group irrecoverable on a downstream failure. The fix uses a Hazelcast
     * transaction to atomically apply the conditional remove and the recovery write, so the loser's transaction sees
     * the entry already removed and rolls back without touching the persistent map.
     * <p>
     * Scenario: add a group, get it twice as {@code a} and {@code b} (same content, same exchangeId), remove(a)
     * succeeds, remove(b) throws {@link OptimisticLockingException}. After the failed remove, recover(exchangeId) must
     * still return the group so that downstream failures can be recovered.
     * <p>
     * Note: the transaction also closes the crash-between-two-steps window that CAMEL-26663 originally fixed: if the
     * JVM stops after the conditional remove but before the recovery write, the transaction is rolled back on the
     * cluster side (owner failure or timeout) and the group re-appears in the operational repository.
     */
    @Test
    public void checkOptimisticRemoveRaceDoesNotDeleteWinnersRecoveryEntry() throws Exception {
        final String persistentRepoName = "optimisticRaceRepo-completed";
        HazelcastAggregationRepository repoOne
                = new HazelcastAggregationRepository("optimisticRaceRepo", persistentRepoName, true, getFirstInstance());
        HazelcastAggregationRepository repoTwo
                = new HazelcastAggregationRepository("optimisticRaceRepo", persistentRepoName, true, getSecondInstance());

        try {
            repoOne.doStart();
            repoTwo.doStart();

            final String key = "raceKey";
            Exchange ex = createExchangeWithBody("The group body.");
            repoOne.add(context(), key, null, ex);

            // Both threads read the same stored exchange (same exchangeId, same content)
            Exchange a = repoOne.get(context(), key);
            Exchange b = repoTwo.get(context(), key);
            assertNotNull(a, "Thread A's exchange should not be null");
            assertNotNull(b, "Thread B's exchange should not be null");
            assertEquals(a.getExchangeId(), b.getExchangeId(), "Both threads must hold the same exchangeId");

            // Thread A wins the race: remove succeeds and a recovery entry is written atomically
            repoOne.remove(context(), key, a);

            // Thread B loses: compare-and-remove fails (entry already removed by A)
            assertThrows(OptimisticLockingException.class, () -> repoTwo.remove(context(), key, b));

            // Thread A's recovery entry must still be there: if A's downstream processing fails,
            // the recovery task must be able to send the group
            Exchange recovered = repoTwo.recover(context(), a.getExchangeId());
            assertNotNull(recovered, "The winner's recovery entry must survive the loser's failed remove");
            assertEquals(ex.getIn().getBody(), recovered.getIn().getBody());
        } finally {
            repoOne.doStop();
            repoTwo.doStop();
        }
    }

}
