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
package org.apache.camel.component.aws.ddbstream;

import java.util.ArrayList;
import java.util.List;

import com.amazonaws.services.dynamodbv2.model.SequenceNumberRange;
import com.amazonaws.services.dynamodbv2.model.Shard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ShardListTest {

    @Test
    public void nextReturnsShardWithParent() throws Exception {
        Shard first = new Shard()
                .withShardId("first_shard")
                .withParentShardId("other_shard_id");
        Shard second = new Shard()
                .withParentShardId("first_shard")
                .withShardId("second_shard");

        ShardList shards = new ShardList();
        shards.add(first);
        shards.add(second);

        assertEquals(second, shards.nextAfter(first));
    }

    @Test
    public void nextWithNullReturnsFirstKnownShard() throws Exception {
        Shard first = new Shard()
                .withShardId("first_shard");
        Shard second = new Shard()
                .withParentShardId("first_shard")
                .withShardId("second_shard");

        ShardList shards = new ShardList();
        shards.add(first);
        shards.add(second);

        assertEquals(second, shards.nextAfter(first));
    }

    @Test
    public void reAddingEntriesMaintainsOrder() throws Exception {
        Shard first = new Shard()
                .withShardId("first_shard");
        Shard second = new Shard()
                .withParentShardId("first_shard")
                .withShardId("second_shard");

        ShardList shards = new ShardList();
        shards.add(first);
        shards.add(second);

        assertEquals(second, shards.nextAfter(first));

        Shard second2 = new Shard()
                .withParentShardId("first_shard")
                .withShardId("second_shard");
        Shard third = new Shard()
                .withParentShardId("second_shard")
                .withShardId("third_shard");
        shards.add(second2);
        shards.add(third);

        assertEquals(second, shards.nextAfter(first));
        assertEquals(third, shards.nextAfter(second));
    }

    @Test
    public void firstShardGetsTheFirstWithoutAParent() throws Exception {
        ShardList shards = new ShardList();
        shards.addAll(createShards(null, "a", "b", "c", "d"));

        assertEquals("a", shards.first().getShardId());
    }

    @Test
    public void firstShardGetsTheFirstWithAnUnknownParent() throws Exception {
        ShardList shards = new ShardList();
        shards.addAll(createShards("a", "b", "c", "d"));

        assertEquals("b", shards.first().getShardId());
    }

    @Test
    public void lastShardGetsTheShardWithNoChildren() throws Exception {
        ShardList shards = new ShardList();
        shards.addAll(createShards("a", "b", "c", "d"));

        assertEquals("d", shards.last().getShardId());
    }

    @Test
    public void removingShards() throws Exception {
        ShardList shards = new ShardList();
        shards.addAll(createShards(null, "a", "b", "c", "d"));
        Shard removeBefore = new Shard().withShardId("c").withParentShardId("b");
        shards.removeOlderThan(removeBefore);

        assertEquals("c", shards.first().getShardId());
    }

    static List<Shard> createShardsWithSequenceNumbers(String initialParent, String... shardIdsAndSeqNos) {
        String previous = initialParent;
        List<Shard> result = new ArrayList<>();
        for (int i = 0; i < shardIdsAndSeqNos.length; i += 3) {
            String id = shardIdsAndSeqNos[i];
            String seqStart = shardIdsAndSeqNos[i + 1];
            String seqEnd = shardIdsAndSeqNos[i + 2];
            result.add(new Shard()
                    .withShardId(id)
                    .withParentShardId(previous)
                    .withSequenceNumberRange(new SequenceNumberRange()
                            .withStartingSequenceNumber(seqStart)
                            .withEndingSequenceNumber(seqEnd)));
            previous = id;
        }
        return result;
    }

    static List<Shard> createShards(String initialParent, String... shardIds) {
        String previous = initialParent;
        List<Shard> result = new ArrayList<>();
        for (String s : shardIds) {
            result.add(new Shard().withShardId(s).withParentShardId(previous));
            previous = s;
        }
        return result;
    }
}
