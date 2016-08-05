/**
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
package org.apache.camel.component.ignite;

/**
 * Ignite Component constants.
 */
public final class IgniteConstants {

    // Ignite Cache.
    public static final String IGNITE_CACHE_KEY = "CamelIgniteCacheKey";
    public static final String IGNITE_CACHE_QUERY = "CamelIgniteCacheQuery";
    public static final String IGNITE_CACHE_OPERATION = "CamelIgniteCacheOperation";
    public static final String IGNITE_CACHE_PEEK_MODE = "CamelIgniteCachePeekMode";
    public static final String IGNITE_CACHE_EVENT_TYPE = "CamelIgniteCacheEventType";
    public static final String IGNITE_CACHE_NAME = "CamelIgniteCacheName";
    public static final String IGNITE_CACHE_OLD_VALUE = "CamelIgniteCacheOldValue";

    // Ignite Messaging.
    public static final String IGNITE_MESSAGING_TOPIC = "CamelIgniteMessagingTopic";
    public static final String IGNITE_MESSAGING_UUID = "CamelIgniteMessagingUUID";

    // Ignite Compute.
    public static final String IGNITE_COMPUTE_EXECUTION_TYPE = "CamelIgniteComputeExecutionType";
    public static final String IGNITE_COMPUTE_PARAMS = "CamelIgniteComputeParameters";
    public static final String IGNITE_COMPUTE_REDUCER = "CamelIgniteComputeReducer";
    public static final String IGNITE_COMPUTE_AFFINITY_CACHE_NAME = "CamelIgniteComputeAffinityCacheName";
    public static final String IGNITE_COMPUTE_AFFINITY_KEY = "CamelIgniteComputeAffinityKey";

    // Ignite Sets.
    public static final String IGNITE_SETS_OPERATION = "CamelIgniteSetsOperation";

    // Ignite ID Gen.
    public static final String IGNITE_IDGEN_OPERATION = "CamelIgniteIdGenOperation";

    // Ignite Queues.
    public static final String IGNITE_QUEUE_OPERATION = "CamelIgniteQueueOperation";
    public static final String IGNITE_QUEUE_MAX_ELEMENTS = "CamelIgniteQueueMaxElements";
    public static final String IGNITE_QUEUE_TRANSFERRED_COUNT = "CamelIgniteQueueTransferredCount";
    public static final String IGNITE_QUEUE_TIMEOUT_MILLIS = "CamelIgniteQueueTimeoutMillis";
    
    private IgniteConstants() { }

}
