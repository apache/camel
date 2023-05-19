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
package org.apache.camel.component.aws2.ddbstream;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import software.amazon.awssdk.services.dynamodb.model.Shard;

class ShardTree {

    private final Map<String, Shard> shards = new HashMap<>();

    void populate(Collection<Shard> shards) {
        this.shards.clear();
        for (Shard shard : shards) {
            this.shards.put(shard.shardId(), shard);
        }
    }

    List<Shard> getRoots() {
        List<Shard> roots = shards.values()
                .stream()
                .filter(s -> !shards.containsKey(s.parentShardId()))
                .toList();
        if (roots.isEmpty()) {
            throw new IllegalStateException("Unable to find an unparented shard in " + shards);
        }
        return roots;
    }

    List<Shard> getLeaves() {
        return shards.values()
                .stream()
                .filter(s -> s.sequenceNumberRange().endingSequenceNumber() == null)
                .toList();
    }

    List<Shard> getChildren(String shardId) {
        return shards.values()
                .stream()
                .filter(s -> shardId.equals(s.parentShardId()))
                .toList();
    }

    @Override
    public String toString() {
        return "ShardList{" + "shards=" + shards + '}';
    }

}
