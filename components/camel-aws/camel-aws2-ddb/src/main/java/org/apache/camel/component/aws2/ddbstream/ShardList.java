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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.model.Shard;

class ShardList {

    private final Logger log = LoggerFactory.getLogger(ShardList.class);

    private final Map<String, Shard> shards = new HashMap<>();

    void addAll(Collection<Shard> shards) {
        for (Shard shard : shards) {
            add(shard);
        }
    }

    void add(Shard shard) {
        shards.put(shard.shardId(), shard);
    }

    Shard nextAfter(Shard previous) {
        for (Shard shard : shards.values()) {
            if (previous.shardId().equals(shard.parentShardId())) {
                return shard;
            }
        }
        throw new IllegalStateException("Unable to find the next shard for " + previous + " in " + shards);
    }

    Shard first() {
        // Potential optimisation: if the two provided sequence numbers are the
        // same then we can skip the shard entirely. Need to confirm this with
        // AWS.
        for (Shard shard : shards.values()) {
            if (!shards.containsKey(shard.parentShardId())) {
                return shard;
            }
        }
        throw new IllegalStateException("Unable to find an unparented shard in " + shards);
    }

    Shard last() {
        Map<String, Shard> shardsByParent = new HashMap<>();
        for (Shard shard : shards.values()) {
            shardsByParent.put(shard.parentShardId(), shard);
        }
        for (Shard shard : shards.values()) {
            if (!shardsByParent.containsKey(shard.shardId())) {
                return shard;
            }
        }
        throw new IllegalStateException("Unable to find a shard with no children " + shards);
    }

    Shard afterSeq(String sequenceNumber) {
        return atAfterSeq(sequenceNumber, BigIntComparisons.Conditions.LT);
    }

    Shard atSeq(String sequenceNumber) {
        return atAfterSeq(sequenceNumber, BigIntComparisons.Conditions.LTEQ);
    }

    Shard atAfterSeq(String sequenceNumber, BigIntComparisons condition) {
        BigInteger atAfter = new BigInteger(sequenceNumber);
        List<Shard> sorted = new ArrayList<>(shards.values());
        Collections.sort(sorted, StartingSequenceNumberComparator.INSTANCE);
        for (Shard shard : sorted) {
            if (shard.sequenceNumberRange().endingSequenceNumber() != null) {
                BigInteger end = new BigInteger(shard.sequenceNumberRange().endingSequenceNumber());
                // essentially: after < end or after <= end
                if (condition.matches(atAfter, end)) {
                    return shard;
                }

            }
        }
        if (shards.size() > 0) {
            return sorted.get(sorted.size() - 1);
        }
        throw new IllegalStateException(
                "Unable to find a shard with appropriate sequence numbers for " + sequenceNumber + " in " + shards);
    }

    /**
     * Removes shards that are older than the provided shard. Does not remove the provided shard.
     *
     * @param removeBefore
     */
    void removeOlderThan(Shard removeBefore) {
        String current = removeBefore.parentShardId();

        int removedShards = 0;
        while (current != null) {
            Shard s = shards.remove(current);
            if (s == null) {
                current = null;
            } else {
                removedShards++;
                current = s.parentShardId();
            }
        }
        log.trace("removed {} shards from the store, new size is {}", removedShards, shards.size());
    }

    @Override
    public String toString() {
        return "ShardList{" + "shards=" + shards + '}';
    }

    private enum StartingSequenceNumberComparator implements Comparator<Shard> {
        INSTANCE() {
            @Override
            public int compare(Shard o1, Shard o2) {
                BigInteger i1 = new BigInteger(o1.sequenceNumberRange().startingSequenceNumber());
                BigInteger i2 = new BigInteger(o2.sequenceNumberRange().startingSequenceNumber());
                return i1.compareTo(i2);
            }
        }
    }
}
