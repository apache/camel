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
package org.apache.camel.component.aws.ddbstream;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.dynamodbv2.model.Shard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ShardList {
    private final Logger log = LoggerFactory.getLogger(ShardList.class);

    private final Map<String, Shard> shards = new HashMap<>();

    void addAll(Collection<Shard> shards) {
        for (Shard shard : shards) {
            add(shard);
        }
    }

    void add(Shard shard) {
        shards.put(shard.getShardId(), shard);
    }

    Shard nextAfter(Shard previous) {
        for (Shard shard : shards.values()) {
            if (previous.getShardId().equals(shard.getParentShardId())) {
                return shard;
            }
        }
        throw new IllegalStateException("Unable to find the next shard for " + previous + " in " + shards);
    }

    Shard first() {
        for (Shard shard : shards.values()) {
            if (!shards.containsKey(shard.getParentShardId())) {
                return shard;
            }
        }
        throw new IllegalStateException("Unable to find an unparented shard in " + shards);
    }

    Shard last() {
        Map<String, Shard> shardsByParent = new HashMap<>();
        for (Shard shard : shards.values()) {
            shardsByParent.put(shard.getParentShardId(), shard);
        }
        for (Shard shard : shards.values()) {
            if (!shardsByParent.containsKey(shard.getShardId())) {
                return shard;
            }
        }
        throw new IllegalStateException("Unable to find a shard with no children " + shards);
    }

    /**
     * Removes shards that are older than the provided shard.
     * Does not remove the provided shard.
     * @param removeBefore
     */
    void removeOlderThan(Shard removeBefore) {
        String current = removeBefore.getParentShardId();

        int removedShards = 0;
        while (current != null) {
            Shard s = shards.remove(current);
            if (s == null) {
                current = null;
            } else {
                removedShards++;
                current = s.getParentShardId();
            }
        }
        log.trace("removed {} shards from the store, new size is {}", removedShards, shards.size());
    }

    @Override
    public String toString() {
        return "ShardList{" + "shards=" + shards + '}';
    }
}