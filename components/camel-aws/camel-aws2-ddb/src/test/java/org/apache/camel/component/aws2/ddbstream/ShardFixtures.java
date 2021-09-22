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

import software.amazon.awssdk.services.dynamodb.model.SequenceNumberRange;
import software.amazon.awssdk.services.dynamodb.model.Shard;

final class ShardFixtures {

    public static final String STREAM_ARN = "arn:aws:dynamodb:eu-west-1:1234:table/some-table/stream/2021-05-07T09:03:40.295";

    //
    //               shard 0
    //             /        \
    //       shard 1         shard 2
    //      /      \        /       \
    // shard 3   shard 4  shard 5   shard 6
    //
    public static final Shard SHARD_0 = Shard.builder()
            .shardId("SHARD_0")
            .sequenceNumberRange(SequenceNumberRange.builder().startingSequenceNumber("4100000000019118544662")
                    .endingSequenceNumber("4100000000019118559460").build())
            .build();
    public static final Shard SHARD_1 = Shard.builder()
            .shardId("SHARD_1")
            .parentShardId("SHARD_0")
            .sequenceNumberRange(SequenceNumberRange.builder().startingSequenceNumber("52700000000007125283545")
                    .endingSequenceNumber("52700000000007125283545").build())
            .build();
    public static final Shard SHARD_2 = Shard.builder()
            .shardId("SHARD_2")
            .parentShardId("SHARD_0")
            .sequenceNumberRange(SequenceNumberRange.builder().startingSequenceNumber("52700000000020262580503")
                    .endingSequenceNumber("52700000000020262580503").build())
            .build();
    public static final Shard SHARD_3 = Shard.builder()
            .shardId("SHARD_3")
            .parentShardId("SHARD_1")
            .sequenceNumberRange(SequenceNumberRange.builder().startingSequenceNumber("74100000000005516202603").build())
            .build();
    public static final Shard SHARD_4 = Shard.builder()
            .shardId("SHARD_4")
            .parentShardId("SHARD_1")
            .sequenceNumberRange(SequenceNumberRange.builder().startingSequenceNumber("74100000000044018483325").build())
            .build();
    public static final Shard SHARD_5 = Shard.builder()
            .shardId("SHARD_5")
            .parentShardId("SHARD_2")
            .sequenceNumberRange(SequenceNumberRange.builder().startingSequenceNumber("105800000000033207048658").build())
            .build();
    public static final Shard SHARD_6 = Shard.builder()
            .shardId("SHARD_6")
            .parentShardId("SHARD_2")
            .sequenceNumberRange(SequenceNumberRange.builder().startingSequenceNumber("105800000000025199618049").build())
            .build();

    private ShardFixtures() {
        // Utility class, not called.
    }
}
