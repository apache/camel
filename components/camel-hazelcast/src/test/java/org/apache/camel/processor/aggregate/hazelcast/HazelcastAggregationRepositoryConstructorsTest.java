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
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HazelcastAggregationRepositoryConstructorsTest extends CamelTestSupport {

    @Test
    public void nonOptimisticRepoFailsOnOptimisticAdd() throws Exception {
        final String repoName = "hzRepoMap";
        HazelcastAggregationRepository repo = new HazelcastAggregationRepository(repoName);
        repo.doStart();

        try {
            final CamelContext context = context();
            Exchange oldOne = new DefaultExchange(context);
            Exchange newOne = new DefaultExchange(context);
            final String key = "abrakadabra";

            assertThrows(UnsupportedOperationException.class,
                    () -> repo.add(context, key, oldOne, newOne));
        } finally {
            repo.doStop();
        }
    }

    @Test
    public void optimisticRepoFailsForNonOptimisticAdd() throws Exception {
        final String repoName = "hzRepoMap";
        HazelcastAggregationRepository repo = new HazelcastAggregationRepository(repoName, true);
        repo.doStart();

        try {
            final CamelContext context = context();
            Exchange ex = new DefaultExchange(context);
            final String key = "abrakadabra";
            assertThrows(UnsupportedOperationException.class,
                    () -> repo.add(context, key, ex));
        } finally {
            repo.doStop();
        }
    }

    @Test
    public void uninitializedHazelcastInstanceThrows() throws Exception {
        final String repoName = "hzRepoMap";
        HazelcastAggregationRepository repo = new HazelcastAggregationRepository(repoName, (HazelcastInstance) null);
        assertThrows(IllegalArgumentException.class,
                () -> repo.doStart());
    }

    @Test
    public void locallyInitializedHazelcastInstanceAdd() {
        assertDoesNotThrow(() -> runLocallyInitializedHazelcastInstanceAdd());
    }

    private void runLocallyInitializedHazelcastInstanceAdd() throws Exception {
        HazelcastAggregationRepository repo = new HazelcastAggregationRepository("hzRepoMap");
        try {
            repo.doStart();
            Exchange ex = new DefaultExchange(context());
            repo.add(context(), "somedefaultkey", ex);
        } finally {
            repo.doStop();
        }
    }
}
