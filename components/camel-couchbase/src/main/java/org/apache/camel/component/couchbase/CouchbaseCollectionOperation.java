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
package org.apache.camel.component.couchbase;

import java.time.Duration;

import com.couchbase.client.core.retry.BestEffortRetryStrategy;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.kv.GetOptions;
import com.couchbase.client.java.kv.GetResult;
import com.couchbase.client.java.kv.MutationResult;
import com.couchbase.client.java.kv.PersistTo;
import com.couchbase.client.java.kv.RemoveOptions;
import com.couchbase.client.java.kv.ReplicateTo;
import com.couchbase.client.java.kv.UpsertOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CouchbaseCollectionOperation {
    private static final Logger LOG = LoggerFactory.getLogger(CouchbaseCollectionOperation.class);

    private CouchbaseCollectionOperation() {
    }

    /**
     * Adds or updates a document in a Collection
     *
     * @param  collection
     * @param  id
     * @param  expiry
     * @param  obj
     * @param  persistTo
     * @param  replicateTo
     * @param  writeQueryTimeout
     * @param  producerRetryPause
     * @return
     */
    protected static Boolean setDocument(
            Collection collection, String id, int expiry, Object obj, PersistTo persistTo, ReplicateTo replicateTo,
            long writeQueryTimeout, long producerRetryPause) {

        UpsertOptions options = UpsertOptions.upsertOptions()
                .expiry(Duration.ofSeconds(expiry))
                .durability(persistTo, replicateTo)
                .timeout(Duration.ofMillis(writeQueryTimeout))
                .retryStrategy(BestEffortRetryStrategy.withExponentialBackoff(Duration.ofMillis(producerRetryPause),
                        Duration.ofMillis(producerRetryPause), 1));

        MutationResult result = collection.upsert(id, obj, options);
        if (LOG.isDebugEnabled()) {
            LOG.debug(result.toString());
        }

        return true;
    }

    /**
     * Gets a document from a Collection
     *
     * @param  collection
     * @param  id
     * @param  queryTimeout
     * @return
     */
    protected static GetResult getDocument(Collection collection, String id, long queryTimeout) {
        GetOptions options = GetOptions.getOptions()
                .timeout(Duration.ofMillis(queryTimeout));
        return collection.get(id, options);
    }

    /**
     * Removes a document from a Collection
     *
     * @param  collection
     * @param  id
     * @param  writeQueryTimeout
     * @param  producerRetryPause
     * @return
     */
    protected static MutationResult removeDocument(
            Collection collection, String id, long writeQueryTimeout, long producerRetryPause) {
        RemoveOptions options = RemoveOptions.removeOptions()
                .timeout(Duration.ofMillis(writeQueryTimeout))
                .retryStrategy(BestEffortRetryStrategy.withExponentialBackoff(Duration.ofMillis(producerRetryPause),
                        Duration.ofMillis(producerRetryPause), 1));
        return collection.remove(id, options);
    }
}
