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
package org.apache.camel.component.jcache.processor.aggregate;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.junit.Test;

public class JCacheAggregationRepositoryTest extends JCacheAggregationRepositoryTestSupport {

    @Test(expected = UnsupportedOperationException.class)
    public void nonOptimisticRepoFailsOnOptimisticAdd() throws Exception {
        JCacheAggregationRepository repo = createRepository(false);
        repo.start();

        try {
            Exchange oldOne = new DefaultExchange(context());
            Exchange newOne = new DefaultExchange(context());
            repo.add(context(), "myKey", oldOne, newOne);

            fail("OptimisticLockingException should has been thrown");
        } finally {
            repo.stop();
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void optimisticRepoFailsForNonOptimisticAdd() throws Exception {
        JCacheAggregationRepository repo = createRepository(true);
        repo.start();

        try {
            Exchange ex = new DefaultExchange(context());
            repo.add(context(), "myKey", ex);
        } finally {
            repo.stop();
        }
    }

    @Test
    public void checkOptimisticAddOfNewExchange() throws Exception {
        JCacheAggregationRepository repoOne = createRepository(true);
        JCacheAggregationRepository repoTwo = createRepository(true);

        repoOne.start();
        repoTwo.start();

        try {
            final String testBody = "This is an optimistic test body. Sincerely yours, Captain Obvious.";
            final String key = "optimisticKey";
            Exchange newEx = createExchangeWithBody(testBody);
            Exchange oldEx = repoOne.add(context(), key, null, newEx);

            assertNull("Old exchange should be null.", oldEx);

            final String theNewestBody = "This is the newest test body.";
            Exchange theNewestEx = createExchangeWithBody(theNewestBody);

            oldEx = repoTwo.add(context(), key, newEx, theNewestEx);
            assertEquals(newEx.getIn().getBody(), oldEx.getIn().getBody());
        } finally {
            repoOne.stop();
            repoTwo.stop();
        }
    }

    @Test
    public void checkThreadSafeAddOfNewExchange() throws Exception {
        JCacheAggregationRepository repoOne = createRepository(false);
        JCacheAggregationRepository repoTwo = createRepository(false);

        repoOne.start();
        repoTwo.start();

        try {
            final String testBody = "This is a thread-safe test body. Sincerely yours, Captain Obvious.";
            final String key = "threadSafeKey";
            Exchange newEx = createExchangeWithBody(testBody);
            Exchange oldEx = repoOne.add(context(), key, newEx);

            assertNull("Old exchange should be null.", oldEx);

            final String theNewestBody = "This is the newest test body.";
            Exchange theNewestEx = createExchangeWithBody(theNewestBody);

            oldEx = repoTwo.add(context(), key, theNewestEx);
            assertEquals(newEx.getIn().getBody(), oldEx.getIn().getBody());
        } finally {
            repoOne.stop();
            repoTwo.stop();
        }
    }

    @Test
    public void checkOptimisticGet() throws Exception {
        JCacheAggregationRepository repoOne = createRepository(true);
        JCacheAggregationRepository repoTwo = createRepository(true);

        repoOne.start();
        repoTwo.start();

        try {
            final String testBody = "This is an optimistic test body. Sincerely yours, Captain Obvious.";
            final String key = "optimisticKey";

            Exchange ex = createExchangeWithBody(testBody);
            repoOne.add(context(), key, null, ex);

            Exchange gotEx = repoTwo.get(context(), key);
            assertEquals("ex and gotEx should be equal", gotEx.getIn().getBody(), ex.getIn().getBody());
        } finally {
            repoOne.stop();
            repoTwo.stop();
        }
    }

    @Test
    public void checkThreadSafeGet() throws Exception {
        JCacheAggregationRepository repoOne = createRepository(false);
        JCacheAggregationRepository repoTwo = createRepository(false);

        repoOne.start();
        repoTwo.start();

        try {
            final String testBody = "This is a thread-safe test body. Sincerely yours, Captain Obvious.";
            final String key = "threadSafeKey";

            Exchange ex = createExchangeWithBody(testBody);
            repoOne.add(context(), key, ex);

            Exchange gotEx = repoTwo.get(context(), key);
            assertEquals("ex and gotEx should be equal", gotEx.getIn().getBody(), ex.getIn().getBody());
        } finally {
            repoOne.stop();
            repoTwo.stop();
        }
    }
}
