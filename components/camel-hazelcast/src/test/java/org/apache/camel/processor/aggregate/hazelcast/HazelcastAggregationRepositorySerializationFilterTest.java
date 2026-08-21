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

import com.hazelcast.config.JavaSerializationFilterConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Both aggregation repositories bootstrap their own Hazelcast instance when none is injected.
 * {@link ReplicatedHazelcastAggregationRepository} overrides {@code doStart()} without calling {@code super.doStart()},
 * so the two bootstrap paths have to be asserted separately to keep them from drifting apart again.
 */
class HazelcastAggregationRepositorySerializationFilterTest {

    @Test
    void locallyInitializedInstanceCarriesTheDefaultFilter() throws Exception {
        assertDefaultFilterApplied(new HazelcastAggregationRepository("hzFilterRepoMap"));
    }

    @Test
    void replicatedLocallyInitializedInstanceCarriesTheDefaultFilter() throws Exception {
        assertDefaultFilterApplied(new ReplicatedHazelcastAggregationRepository("hzFilterReplicatedRepoMap"));
    }

    private static void assertDefaultFilterApplied(HazelcastAggregationRepository repo) throws Exception {
        repo.doStart();
        try {
            JavaSerializationFilterConfig filter = repo.getHazelcastInstance()
                    .getConfig().getSerializationConfig().getJavaSerializationFilterConfig();

            assertThat(filter).isNotNull();
            assertThat(filter.getWhitelist().getPrefixes()).contains("java.", "javax.", "org.apache.camel.");
            assertThat(filter.getBlacklist().getPrefixes()).contains("java.net.");
        } finally {
            repo.doStop();
        }
    }
}
