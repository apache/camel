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
package org.apache.camel.component.aws2.kinesis;

import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;

public interface Kinesis2Constants {

    @Metadata(description = "The sequence number of the record, as defined in\n" +
                            "http://docs.aws.amazon.com/kinesis/latest/APIReference/API_PutRecord.html#API_PutRecord_ResponseSyntax[Response Syntax]",
              javaType = "String")
    String SEQUENCE_NUMBER = "CamelAwsKinesisSequenceNumber";
    @Metadata(description = "The time AWS assigned as the arrival time of the record.", javaType = "String")
    String APPROX_ARRIVAL_TIME = "CamelAwsKinesisApproximateArrivalTimestamp";
    @Metadata(description = "Identifies which shard in the stream the data record is assigned to.", javaType = "String")
    String PARTITION_KEY = "CamelAwsKinesisPartitionKey";
    @Metadata(description = "The timestamp of the message", javaType = "long")
    String MESSAGE_TIMESTAMP = Exchange.MESSAGE_TIMESTAMP;

    /**
     * in a Kinesis Record object, the shard ID is used on writes to indicate where the data was stored
     */
    @Metadata(description = "The shard ID of the shard where the data record was placed.", javaType = "String")
    String SHARD_ID = "CamelAwsKinesisShardId";
}
