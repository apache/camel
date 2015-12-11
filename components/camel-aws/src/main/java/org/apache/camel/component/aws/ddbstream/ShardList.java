package org.apache.camel.component.aws.ddbstream;

import com.amazonaws.services.dynamodbv2.model.Shard;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

class ShardList {

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

    /**
     * Removes shards that are older than the provided shard.
     * Does not remove the provided shard.
     * @param removeBefore
     */
    void removeOlderThan(Shard removeBefore) {
        String current = removeBefore.getParentShardId();

        while (current != null) {
            Shard s = shards.remove(current);
            if (s == null) {
                current = null;
            } else {
                current = s.getParentShardId();
            }
        }
    }
}