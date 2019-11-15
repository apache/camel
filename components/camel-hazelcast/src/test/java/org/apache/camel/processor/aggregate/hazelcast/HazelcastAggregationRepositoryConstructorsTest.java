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

import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class HazelcastAggregationRepositoryConstructorsTest extends CamelTestSupport {

    @Test(expected = UnsupportedOperationException.class)
    public void nonOptimisticRepoFailsOnOptimisticAdd() throws Exception {
        final String repoName = "hzRepoMap";
        HazelcastAggregationRepository repo = new HazelcastAggregationRepository(repoName);
        repo.doStart();

        try {
            Exchange oldOne = new DefaultExchange(context());
            Exchange newOne = new DefaultExchange(context());
            final String key = "abrakadabra";
            repo.add(context(), key, oldOne, newOne);
            fail("OptimisticLockingException should has been thrown");
        } finally {
            repo.doStop();
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void optimisticRepoFailsForNonOptimisticAdd() throws Exception {
        final String repoName = "hzRepoMap";
        HazelcastAggregationRepository repo = new HazelcastAggregationRepository(repoName, true);
        repo.doStart();

        try {
            Exchange ex = new DefaultExchange(context());
            final String key = "abrakadabra";
            repo.add(context(), key, ex);
        } finally {
            repo.doStop();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void uninitializedHazelcastInstanceThrows() throws Exception {
        final String repoName = "hzRepoMap";
        HazelcastAggregationRepository repo = new HazelcastAggregationRepository(repoName, (HazelcastInstance) null);
        repo.doStart();
    }

    @Test
    public void locallyInitializedHazelcastInstanceAdd() throws Exception {
        HazelcastAggregationRepository repo = new HazelcastAggregationRepository("hzRepoMap");
        try {
            repo.doStart();
            Exchange ex = new DefaultExchange(context());
            repo.add(context(), "somedefaultkey", ex);
        //} catch (Throwable e) {
            //fail(e.getMessage());
        } finally {
            repo.doStop();
        }
    }
}
