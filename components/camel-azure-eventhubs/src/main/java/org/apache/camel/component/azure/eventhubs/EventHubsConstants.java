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

public final class EventHubsConstants {
    private static final String HEADER_PREFIX = "CamelAzureEventHubs";
    // common headers, set by consumer and evaluated by producer
    public static final String PARTITION_KEY = HEADER_PREFIX + "PartitionKey";
    public static final String PARTITION_ID = HEADER_PREFIX + "PartitionId";
    // headers set by the consumer only
    public static final String OFFSET = HEADER_PREFIX + "Offset";
    public static final String ENQUEUED_TIME = HEADER_PREFIX + "EnqueuedTime";
    public static final String SEQUENCE_NUMBER = HEADER_PREFIX + "SequenceNumber";

    private EventHubsConstants() {
    }
}
