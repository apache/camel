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
package org.apache.camel.component.azure.eventhubs;

import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;

public final class EventHubsConstants {
    public static final String HEADER_PREFIX = "CamelAzureEventHubs";
    public static final String COMPLETED_BY_SIZE = "size";
    public static final String COMPLETED_BY_TIMEOUT = "timeout";
    public static final String UNCOMPLETED = "uncompleted";
    // common headers, set by consumer and evaluated by producer
    @Metadata(description = "(producer) Overrides the hashing key to be provided for the batch of events, which instructs the Event Hubs service to map this key to a specific partition.\n"
                            +
                            "(consumer) It sets the partition hashing key if it was set when originally publishing the event. "
                            +
                            "If it exists, this value was used to compute a hash to select a partition to send the message to. This is only present on a received `EventData`.",
              javaType = "String")
    public static final String PARTITION_KEY = HEADER_PREFIX + "PartitionKey";
    @Metadata(description = "(producer) Overrides the identifier of the Event Hub partition that the events will be sent to.\n"
                            +
                            "(consumer) It sets the partition id of the Event Hub.",
              javaType = "String")
    public static final String PARTITION_ID = HEADER_PREFIX + "PartitionId";
    // headers set by the consumer only
    @Metadata(label = "consumer",
              description = "It sets the offset of the event when it was received from the associated Event Hub partition. This is only present on a received `EventData`.",
              javaType = "Integer")
    public static final String OFFSET = HEADER_PREFIX + "Offset";
    @Metadata(label = "consumer",
              description = "It sets the instant, in UTC, of when the event was enqueued in the Event Hub partition. This is only present on a received `EventData`.",
              javaType = "Instant")
    public static final String ENQUEUED_TIME = HEADER_PREFIX + "EnqueuedTime";
    @Metadata(label = "consumer",
              description = "It sets the sequence number assigned to the event when it was enqueued in the associated Event Hub partition. "
                            +
                            "This is unique for every message received in the Event Hub partition. This is only present on a received `EventData`.",
              javaType = "Long")
    public static final String SEQUENCE_NUMBER = HEADER_PREFIX + "SequenceNumber";
    @Metadata(label = "consumer",
              description = "The set of free-form event properties which may be used for passing metadata associated with the event with the event body during Event Hubs operations.",
              javaType = "Map<String, Object>")
    public static final String METADATA = HEADER_PREFIX + "Metadata";
    @Metadata(label = "consumer", description = "The timestamp of the message", javaType = "long")
    public static final String MESSAGE_TIMESTAMP = Exchange.MESSAGE_TIMESTAMP;
    @Metadata(label = "consumer",
              description = "It sets the reason for the checkpoint to have been updated. This is only present on a received `EventData`.",
              javaType = "String")
    public static final String CHECKPOINT_UPDATED_BY = HEADER_PREFIX + "CheckpointUpdatedBy";

    private EventHubsConstants() {
    }
}
