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

package org.apache.camel.component.aws2.kinesis.consumer;

import org.apache.camel.resume.ResumeAdapter;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorRequest;

/**
 * The resume adapter for Kinesis
 */
public interface KinesisResumeAdapter extends ResumeAdapter {
    /*
        When consuming from multiple shards the KinesisResumeAdapter is potentially accessed by multiple threads.
        To avoid any concurrency issues the configuration of the GetShardIteratorRequest should be done in one operation
        and not using multiple calls like in the previous version of this interface
     */
    void configureGetShardIteratorRequest(GetShardIteratorRequest.Builder builder, String streamName, String shardId);
}
