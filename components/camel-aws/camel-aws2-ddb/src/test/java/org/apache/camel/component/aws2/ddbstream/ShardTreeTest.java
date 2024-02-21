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

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.Shard;

import static org.apache.camel.component.aws2.ddbstream.ShardFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShardTreeTest {

    private final ShardTree underTest = new ShardTree();

    @Test
    void shouldGetLeafShards() {
        underTest.populate(Arrays.asList(SHARD_0, SHARD_1, SHARD_2, SHARD_3, SHARD_4, SHARD_5, SHARD_6));

        assertEquals(Arrays.asList(SHARD_5, SHARD_6, SHARD_3, SHARD_4), underTest.getLeaves());
    }

    @Test
    void shouldReturnEmptyListIfAllShardsHaveAnEndingSequenceNumber() {
        underTest.populate(Arrays.asList(SHARD_0, SHARD_1, SHARD_2));

        assertEquals(Arrays.asList(), underTest.getLeaves());
    }

    @Test
    void shouldGetRootShards() {
        underTest.populate(Arrays.asList(SHARD_1, SHARD_2, SHARD_3, SHARD_4, SHARD_5, SHARD_6));

        assertEquals(Arrays.asList(SHARD_1, SHARD_2), underTest.getRoots());
    }

    @Test
    void shouldThrowIfNoUnparentedShardsCanBeFound() {
        Shard selfParentingShard = Shard.builder().shardId("SHARD_X").parentShardId("SHARD_X").build();
        underTest.populate(Arrays.asList(selfParentingShard));

        assertThrows(IllegalStateException.class, () -> underTest.getRoots());
    }

    @Test
    void shouldGetChildShards() {
        underTest.populate(Arrays.asList(SHARD_0, SHARD_1, SHARD_2, SHARD_3, SHARD_4, SHARD_5, SHARD_6));

        assertEquals(Arrays.asList(SHARD_5, SHARD_6), underTest.getChildren("SHARD_2"));
    }

    @Test
    void shouldReturnEmptyListIfTheShardHasNoChildren() {
        underTest.populate(Arrays.asList(SHARD_6));

        assertEquals(Arrays.asList(), underTest.getChildren("SHARD_6"));
    }

}
