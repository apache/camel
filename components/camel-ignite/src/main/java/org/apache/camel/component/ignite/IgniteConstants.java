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
package org.apache.camel.component.ignite;

import org.apache.camel.spi.Metadata;

/**
 * Ignite Component constants.
 */
public final class IgniteConstants {

    // Schemes
    public static final String SCHEME_CACHE = "ignite-cache";
    public static final String SCHEME_COMPUTE = "ignite-compute";
    public static final String SCHEME_MESSAGING = "ignite-messaging";
    public static final String SCHEME_QUEUE = "ignite-queue";
    public static final String SCHEME_SET = "ignite-set";
    public static final String SCHEME_EVENTS = "ignite-events";
    public static final String SCHEME_IDGEN = "ignite-idgen";

    // Ignite Cache.
    @Metadata(description = "The cache key for the entry value in the message body.", javaType = "Object",
              applicableFor = SCHEME_CACHE)
    public static final String IGNITE_CACHE_KEY = "CamelIgniteCacheKey";
    @Metadata(label = "producer", description = "The query to run when invoking the QUERY operation.",
              javaType = "org.apache.ignite.cache.query.Query", applicableFor = SCHEME_CACHE)
    public static final String IGNITE_CACHE_QUERY = "CamelIgniteCacheQuery";
    @Metadata(label = "producer", description = "Allows you to dynamically change the cache operation to execute.",
              javaType = "org.apache.camel.component.ignite.cache.IgniteCacheOperation", applicableFor = SCHEME_CACHE)
    public static final String IGNITE_CACHE_OPERATION = "CamelIgniteCacheOperation";
    @Metadata(label = "producer",
              description = "Allows you to dynamically change the cache peek mode when running the SIZE operation.",
              javaType = "org.apache.ignite.cache.CachePeekMode", applicableFor = SCHEME_CACHE)
    public static final String IGNITE_CACHE_PEEK_MODE = "CamelIgniteCachePeekMode";
    @Metadata(label = "consumer",
              description = "This header carries the received event type when using the continuous query consumer.",
              javaType = "javax.cache.event.EventType", applicableFor = SCHEME_CACHE)
    public static final String IGNITE_CACHE_EVENT_TYPE = "CamelIgniteCacheEventType";
    @Metadata(label = "consumer",
              description = "This header carries the cache name for which a continuous query event was received (consumer).\n" +
                            "It does not allow you to dynamically change the cache against which a producer operation is performed. Use EIPs for that (e.g. recipient list, dynamic router).",
              javaType = "String", applicableFor = SCHEME_CACHE)
    public static final String IGNITE_CACHE_NAME = "CamelIgniteCacheName";
    @Metadata(description = "(producer) The old cache value to be replaced when invoking the REPLACE operation. \n" +
                            "(consumer) This header carries the old cache value when passed in the incoming cache event.",
              javaType = "Object", applicableFor = SCHEME_CACHE)
    public static final String IGNITE_CACHE_OLD_VALUE = "CamelIgniteCacheOldValue";

    // Ignite Messaging.
    @Metadata(description = "(producer) Allows you to dynamically change the topic to send messages to. \n" +
                            "(consumer) It also carries the topic on which a message was received.",
              javaType = "String", applicableFor = SCHEME_MESSAGING)
    public static final String IGNITE_MESSAGING_TOPIC = "CamelIgniteMessagingTopic";
    @Metadata(label = "consumer",
              description = "This header is filled in with the UUID of the subscription when a message arrives.",
              javaType = "java.util.UUID", applicableFor = SCHEME_MESSAGING)
    public static final String IGNITE_MESSAGING_UUID = "CamelIgniteMessagingUUID";

    // Ignite Compute.
    @Metadata(label = "producer", description = "Allows you to dynamically change the compute operation to perform.",
              javaType = "org.apache.camel.component.ignite.compute.IgniteComputeExecutionType", applicableFor = SCHEME_COMPUTE)
    public static final String IGNITE_COMPUTE_EXECUTION_TYPE = "CamelIgniteComputeExecutionType";
    @Metadata(label = "producer", description = "Parameters for APPLY, BROADCAST and EXECUTE operations.",
              javaType = "Any object or Collection of objects", applicableFor = SCHEME_COMPUTE)
    public static final String IGNITE_COMPUTE_PARAMS = "CamelIgniteComputeParameters";
    @Metadata(label = "producer", description = "Reducer for the APPLY and CALL operations.",
              javaType = "org.apache.ignite.lang.IgniteReducer", applicableFor = SCHEME_COMPUTE)
    public static final String IGNITE_COMPUTE_REDUCER = "CamelIgniteComputeReducer";
    @Metadata(label = "producer", description = "Affinity cache name for the AFFINITY_CALL and AFFINITY_RUN operations.",
              javaType = "String", applicableFor = SCHEME_COMPUTE)
    public static final String IGNITE_COMPUTE_AFFINITY_CACHE_NAME = "CamelIgniteComputeAffinityCacheName";
    @Metadata(label = "producer", description = "Affinity key for the AFFINITY_CALL and AFFINITY_RUN operations.",
              javaType = "Object", applicableFor = SCHEME_COMPUTE)
    public static final String IGNITE_COMPUTE_AFFINITY_KEY = "CamelIgniteComputeAffinityKey";

    // Ignite Sets.
    @Metadata(label = "producer", description = "Allows you to dynamically change the set operation.",
              javaType = "org.apache.camel.component.ignite.set.IgniteSetOperation", applicableFor = SCHEME_SET)
    public static final String IGNITE_SETS_OPERATION = "CamelIgniteSetsOperation";

    // Ignite ID Gen.
    @Metadata(label = "producer", description = "Allows you to dynamically change the ID Generator operation.",
              javaType = "org.apache.camel.component.ignite.idgen.IgniteIdGenOperation", applicableFor = SCHEME_IDGEN)
    public static final String IGNITE_IDGEN_OPERATION = "CamelIgniteIdGenOperation";

    // Ignite Queues.
    @Metadata(label = "producer", description = "Allows you to dynamically change the queue operation.",
              javaType = "org.apache.camel.component.ignite.queue.IgniteQueueOperation", applicableFor = SCHEME_QUEUE)
    public static final String IGNITE_QUEUE_OPERATION = "CamelIgniteQueueOperation";
    @Metadata(label = "producer", description = "When invoking the DRAIN operation, the amount of items to drain.",
              javaType = "Integer", applicableFor = SCHEME_QUEUE)
    public static final String IGNITE_QUEUE_MAX_ELEMENTS = "CamelIgniteQueueMaxElements";
    @Metadata(label = "producer", description = "The amount of items transferred as the result of the DRAIN operation.",
              javaType = "Integer", applicableFor = SCHEME_QUEUE)
    public static final String IGNITE_QUEUE_TRANSFERRED_COUNT = "CamelIgniteQueueTransferredCount";
    @Metadata(label = "producer",
              description = "Dynamically sets the timeout in milliseconds to use when invoking the OFFER or POLL operations.",
              javaType = "Long", applicableFor = SCHEME_QUEUE)
    public static final String IGNITE_QUEUE_TIMEOUT_MILLIS = "CamelIgniteQueueTimeoutMillis";

    private IgniteConstants() {
    }

}
