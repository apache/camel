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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import software.amazon.awssdk.services.dynamodb.model.DescribeStreamRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeStreamResponse;
import software.amazon.awssdk.services.dynamodb.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.dynamodb.model.GetShardIteratorResponse;
import software.amazon.awssdk.services.dynamodb.model.ListStreamsRequest;
import software.amazon.awssdk.services.dynamodb.model.ListStreamsResponse;
import software.amazon.awssdk.services.dynamodb.model.Shard;
import software.amazon.awssdk.services.dynamodb.model.Stream;
import software.amazon.awssdk.services.dynamodb.model.StreamDescription;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsServiceClientConfiguration;

import static org.apache.camel.component.aws2.ddbstream.ShardFixtures.STREAM_ARN;

class AmazonDDBStreamsClientMock implements DynamoDbStreamsClient {

    private final Map<Shard, String> shardsToIterators = new HashMap<>();

    @Override
    public ListStreamsResponse listStreams(ListStreamsRequest listStreamsRequest) {
        return ListStreamsResponse.builder().streams(Stream.builder().streamArn(STREAM_ARN).build()).build();
    }

    @Override
    public DynamoDbStreamsServiceClientConfiguration serviceClientConfiguration() {
        return null;
    }

    @Override
    public DescribeStreamResponse describeStream(DescribeStreamRequest describeStreamRequest) {
        return DescribeStreamResponse.builder()
                .streamDescription(StreamDescription.builder().shards(shardsToIterators.keySet()).build()).build();
    }

    @Override
    public GetShardIteratorResponse getShardIterator(GetShardIteratorRequest request) {
        String shardIterator = shardsToIterators.entrySet().stream()
                .filter(s -> s.getKey().shardId().equals(request.shardId()))
                .map(Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No mocked reponse was configured for " + request.shardId()));
        return GetShardIteratorResponse.builder().shardIterator(shardIterator).build();
    }

    @Override
    public String serviceName() {
        return DynamoDbStreamsClient.SERVICE_NAME;
    }

    @Override
    public void close() {
    }

    void setMockedShardAndIteratorResponse(Shard shard, String iterator) {
        shardsToIterators.put(shard, iterator);
    }
}
