/**
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

import org.apache.camel.Exchange;
import org.junit.Test;


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

            assertNull("Old exchange should be null.", oldEx);

            final String theNewestBody = "This is the newest test body.";
            Exchange theNewestEx = createExchangeWithBody(theNewestBody);

            oldEx = repoTwo.add(context(), key, newEx, theNewestEx);
            assertNotNull("oldEx is null", oldEx);
            assertEquals(newEx.getIn().getBody(), oldEx.getIn().getBody());

        } finally {
            repoOne.stop();
            repoTwo.stop();
        }
    }

    @Test
    public void checkThreadSafeAddOfNewExchange() throws Exception {
        HazelcastAggregationRepository repoOne = new HazelcastAggregationRepository(THREAD_SAFE_REPO, false, getFirstInstance());
        HazelcastAggregationRepository repoTwo = new HazelcastAggregationRepository(THREAD_SAFE_REPO, false, getSecondInstance());

        try {
            repoOne.doStart();
            repoTwo.doStart();

            final String testBody = "This is a thread-safe test body. Sincerely yours, Captain Obvious.";
            final String key = "threadSafeKey";
            Exchange newEx = createExchangeWithBody(testBody);
            Exchange oldEx = repoOne.add(context(), key, newEx);

            assertNull("Old exchange should be null.", oldEx);

            final String theNewestBody = "This is the newest test body.";
            Exchange theNewestEx = createExchangeWithBody(theNewestBody);

            oldEx = repoTwo.add(context(), key, theNewestEx);
            assertNotNull("oldEx is null", oldEx);
            assertEquals(newEx.getIn().getBody(), oldEx.getIn().getBody());

        } finally {
            repoOne.stop();
            repoTwo.stop();
        }
    }

    @Test
    public void checkOptimisticGet() throws Exception {
        HazelcastAggregationRepository repoOne = new HazelcastAggregationRepository(THREAD_SAFE_REPO, true, getFirstInstance());
        HazelcastAggregationRepository repoTwo = new HazelcastAggregationRepository(THREAD_SAFE_REPO, true, getSecondInstance());
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
            assertNotNull("gotEx is null", gotEx);
            assertEquals("ex and gotEx should be equal", gotEx.getIn().getBody(), ex.getIn().getBody());
        } finally {
            repoOne.doStop();
            repoTwo.doStop();
        }
    }

    @Test
    public void checkThreadSafeGet() throws Exception {
        HazelcastAggregationRepository repoOne = new HazelcastAggregationRepository(OPTIMISTIC_REPO, false, getFirstInstance());
        HazelcastAggregationRepository repoTwo = new HazelcastAggregationRepository(OPTIMISTIC_REPO, false, getSecondInstance());

        try {
            repoOne.start();
            repoTwo.start();


            final String testBody = "This is a thread-safe test body. Sincerely yours, Captain Obvious.";
            final String key = "threadSafeKey";

            Exchange ex = createExchangeWithBody(testBody);
            repoOne.add(context(), key, ex);

            Exchange gotEx = repoTwo.get(context(), key);
            assertNotNull("gotEx is null", gotEx);
            assertEquals("ex and gotEx should be equal", gotEx.getIn().getBody(), ex.getIn().getBody());
        } finally {
            repoOne.doStop();
            repoTwo.doStop();
        }
    }

    @Test
    public void checkOptimisticPersistentRemove() throws Exception {
        final String persistentRepoName = String.format("%s-completed", OPTIMISTIC_REPO);
        HazelcastAggregationRepository repoOne = new HazelcastAggregationRepository(OPTIMISTIC_REPO, persistentRepoName, true, getFirstInstance());
        HazelcastAggregationRepository repoTwo = new HazelcastAggregationRepository(OPTIMISTIC_REPO, persistentRepoName, true, getSecondInstance());

        try {
            repoOne.doStart();
            repoTwo.doStart();

            final String testBody = "This is an optimistic test body. Sincerely yours, Captain Obvious.";
            final String key = "optimisticPersistentRemoveKey";

            Exchange ex = createExchangeWithBody(testBody);

            repoOne.add(context(), key, null, ex);

            Exchange getBackEx = repoTwo.get(context(), key);
            assertNotNull("getBackEx should not be null.", getBackEx);

            repoTwo.remove(context(), key, ex);

            getBackEx = repoOne.get(context(), key);
            assertNull("getBackEx should be null here.", getBackEx);

            Set<String> keys = repoTwo.scan(context());
            assertCollectionSize(keys, 1);

            getBackEx = repoOne.recover(context(), keys.iterator().next());
            assertNotNull("getBackEx got from persistent repo should not be null.", getBackEx);


        } finally {
            repoOne.doStop();
            repoTwo.doStop();
        }
    }
}
