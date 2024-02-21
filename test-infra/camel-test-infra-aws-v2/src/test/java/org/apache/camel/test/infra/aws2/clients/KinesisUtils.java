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

package org.apache.camel.test.infra.aws2.clients;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.camel.test.infra.common.TestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.CreateStreamRequest;
import software.amazon.awssdk.services.kinesis.model.CreateStreamResponse;
import software.amazon.awssdk.services.kinesis.model.DeleteStreamRequest;
import software.amazon.awssdk.services.kinesis.model.DeleteStreamResponse;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamRequest;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamResponse;
import software.amazon.awssdk.services.kinesis.model.GetRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.GetRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorResponse;
import software.amazon.awssdk.services.kinesis.model.KinesisException;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequestEntry;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.Record;
import software.amazon.awssdk.services.kinesis.model.ResourceInUseException;
import software.amazon.awssdk.services.kinesis.model.ResourceNotFoundException;
import software.amazon.awssdk.services.kinesis.model.Shard;

import static org.junit.jupiter.api.Assertions.fail;

public final class KinesisUtils {
    private static final Logger LOG = LoggerFactory.getLogger(KinesisUtils.class);

    private KinesisUtils() {

    }

    private static void doCreateStream(KinesisClient kinesisClient, String streamName, int shardCount) {
        CreateStreamRequest request = CreateStreamRequest.builder()
                .streamName(streamName)
                .shardCount(shardCount)
                .build();

        try {
            CreateStreamResponse response = kinesisClient.createStream(request);

            if (response.sdkHttpResponse().isSuccessful()) {
                LOG.info("Stream created successfully");
            } else {
                fail("Failed to create the stream");
            }
        } catch (KinesisException e) {
            LOG.error("Unable to create stream: {}", e.getMessage(), e);
            fail("Unable to create stream");
        }
    }

    public static void createStream(KinesisClient kinesisClient, String streamName) {
        createStream(kinesisClient, streamName, 1);
    }

    public static void createStream(KinesisClient kinesisClient, String streamName, int shardCount) {
        try {
            LOG.info("Checking whether the stream exists already");
            int status = getStreamStatus(kinesisClient, streamName);
            LOG.info("Kinesis stream check result: {}", status);
        } catch (KinesisException e) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("The stream does not exist, auto creating it: {}", e.getMessage(), e);
            } else {
                LOG.info("The stream does not exist, auto creating it: {}", e.getMessage());
            }

            doCreateStream(kinesisClient, streamName, shardCount);
            TestUtils.waitFor(() -> {
                try {
                    GetRecordsRequest getRecordsRequest = KinesisUtils.getGetRecordsRequest(kinesisClient, streamName);
                    GetRecordsResponse response = kinesisClient.getRecords(getRecordsRequest);
                    List<Record> recordList = response.records();
                    LOG.debug("Checking for stream creation by reading {} records: SUCCESS!", recordList.size());
                    return true;
                } catch (Exception exc) {
                    LOG.debug("Checking for stream creation by reading records: FAILURE, retrying..");
                    return false;
                }
            });
        } catch (SdkClientException e) {
            LOG.info("SDK Error when getting the stream: {}", e.getMessage());
        }
    }

    private static int getStreamStatus(KinesisClient kinesisClient, String streamName) {
        DescribeStreamRequest request = DescribeStreamRequest.builder()
                .streamName(streamName)
                .build();

        DescribeStreamResponse response = kinesisClient.describeStream(request);

        return response.sdkHttpResponse().statusCode();
    }

    public static void doDeleteStream(KinesisClient kinesisClient, String streamName) {
        DeleteStreamRequest request = DeleteStreamRequest.builder()
                .streamName(streamName)
                .build();

        DeleteStreamResponse response = kinesisClient.deleteStream(request);

        if (response.sdkHttpResponse().isSuccessful()) {
            LOG.info("Stream deleted successfully");
        } else {
            fail("Failed to delete the stream");
        }
    }

    public static void deleteStream(KinesisClient kinesisClient, String streamName) {
        try {
            LOG.info("Checking whether the stream exists already");

            DescribeStreamRequest request = DescribeStreamRequest.builder()
                    .streamName(streamName)
                    .build();

            DescribeStreamResponse response = kinesisClient.describeStream(request);

            if (response.sdkHttpResponse().isSuccessful()) {
                LOG.info("Kinesis stream check result");
                doDeleteStream(kinesisClient, streamName);
            }
        } catch (ResourceNotFoundException e) {
            LOG.info("The stream does not exist, skipping deletion");
        } catch (ResourceInUseException e) {
            LOG.info("The stream exist but cannot be deleted because it's in use");
            doDeleteStream(kinesisClient, streamName);
        }
    }

    public static List<PutRecordsResponse> putRecords(KinesisClient kinesisClient, String streamName, int count) {
        return putRecords(kinesisClient, streamName, count, null);
    }

    public static List<PutRecordsResponse> putRecords(
            KinesisClient kinesisClient, String streamName, int count,
            Consumer<PutRecordsRequest.Builder> customizer) {
        List<PutRecordsRequestEntry> putRecordsRequestEntryList = new ArrayList<>();

        LOG.debug("Adding data to the Kinesis stream");
        for (int i = 0; i < count; i++) {
            String partition = String.format("partitionKey-%d", i);

            PutRecordsRequestEntry putRecordsRequestEntry = PutRecordsRequestEntry.builder()
                    .data(SdkBytes.fromByteArray(String.valueOf(i).getBytes()))
                    .partitionKey(partition)
                    .build();

            LOG.debug("Added data {} (as bytes) to partition {}", i, partition);
            putRecordsRequestEntryList.add(putRecordsRequestEntry);
        }

        LOG.debug("Done creating the data records");

        final PutRecordsRequest.Builder requestBuilder = PutRecordsRequest
                .builder();

        requestBuilder
                .streamName(streamName)
                .records(putRecordsRequestEntryList);

        if (customizer != null) {
            customizer.accept(requestBuilder);
        }

        PutRecordsRequest putRecordsRequest = requestBuilder.build();
        List<PutRecordsResponse> replies = new ArrayList<>(count);

        int retries = 5;
        do {
            try {
                replies.add(kinesisClient.putRecords(putRecordsRequest));
                break;
            } catch (AwsServiceException e) {
                retries--;

                /*
                 This works around the "... Cannot deserialize instance of `...AmazonKinesisException` out of NOT_AVAILABLE token

                 It may take some time for the local Kinesis backend to be fully up - even though the container is
                 reportedly up and running. Therefore, it tries a few more times
                 */
                LOG.trace("Failed to put the records: {}. Retrying in 2 seconds ...", e.getMessage());
                if (retries == 0) {
                    LOG.error("Failed to put the records: {}", e.getMessage(), e);
                    throw e;
                }

                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(2));
                } catch (InterruptedException ex) {
                    break;
                }
            }
        } while (retries > 0);

        return replies;
    }

    private static boolean hasShards(KinesisClient kinesisClient, DescribeStreamRequest describeStreamRequest) {
        DescribeStreamResponse streamRes = kinesisClient.describeStream(describeStreamRequest);

        return !streamRes.streamDescription().shards().isEmpty();
    }

    private static List<Shard> getAllShards(KinesisClient kinesisClient, DescribeStreamRequest describeStreamRequest) {
        List<Shard> shards = new ArrayList<>();
        DescribeStreamResponse streamRes;
        do {
            streamRes = kinesisClient.describeStream(describeStreamRequest);

            shards.addAll(streamRes.streamDescription().shards());
        } while (streamRes.streamDescription().hasMoreShards());

        return shards;
    }

    public static GetRecordsRequest getGetRecordsRequest(KinesisClient kinesisClient, String streamName) {
        DescribeStreamRequest describeStreamRequest = DescribeStreamRequest.builder()
                .streamName(streamName)
                .build();

        TestUtils.waitFor(() -> hasShards(kinesisClient, describeStreamRequest));
        List<Shard> shards = getAllShards(kinesisClient, describeStreamRequest);

        GetShardIteratorRequest iteratorRequest = GetShardIteratorRequest.builder()
                .streamName(streamName)
                .shardId(shards.get(0).shardId())
                .shardIteratorType("TRIM_HORIZON")
                .build();

        GetShardIteratorResponse iteratorResponse = kinesisClient.getShardIterator(iteratorRequest);

        return GetRecordsRequest
                .builder()
                .shardIterator(iteratorResponse.shardIterator())
                .build();
    }
}
