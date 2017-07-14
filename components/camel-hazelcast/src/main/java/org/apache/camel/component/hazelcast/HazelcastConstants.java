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
package org.apache.camel.component.hazelcast;

public final class HazelcastConstants {

    /*
    * uri prefixes
    */
    public static final String MAP_PREFIX = "map:";
    public static final String MULTIMAP_PREFIX = "multimap:";
    public static final String REPLICATEDMAP_PREFIX = "replicatedmap:";
    public static final String ATOMICNUMBER_PREFIX = "atomicvalue:";
    public static final String INSTANCE_PREFIX = "instance:";
    public static final String QUEUE_PREFIX = "queue:";
    public static final String TOPIC_PREFIX = "topic:";
    public static final String SEDA_PREFIX = "seda:";
    public static final String LIST_PREFIX = "list:";
    public static final String SET_PREFIX = "set:";
    public static final String RINGBUFFER_PREFIX = "ringbuffer:";

    /*
     * incoming header properties
     */
    public static final String OBJECT_ID = "CamelHazelcastObjectId";
    public static final String OBJECT_POS = "CamelHazelcastObjectIndex";
    public static final String OBJECT_VALUE = "CamelHazelcastObjectValue";
    public static final String TTL_VALUE = "CamelHazelcastObjectTtlValue";
    public static final String TTL_UNIT = "CamelHazelcastObjectTtlUnit";
    public static final String QUERY = "CamelHazelcastQuery";
    public static final String EXPECTED_VALUE = "CamelHazelcastExpectedValue";
    public static final String DRAIN_TO_COLLECTION = "CamelHazelcastDrainToCollection";

    /*
     * outgoing header properties
     */
    public static final String LISTENER_ACTION = "CamelHazelcastListenerAction";
    public static final String LISTENER_TYPE = "CamelHazelcastListenerType";
    public static final String LISTENER_TIME = "CamelHazelcastListenerTime";
    public static final String INSTANCE_HOST = "CamelHazelcastInstanceHost";
    public static final String INSTANCE_PORT = "CamelHazelcastInstancePort";
    public static final String CACHE_NAME = "CamelHazelcastCacheName";
    public static final String CACHE_TYPE = "CamelHazelcastCacheType";

    // actions (PUT, DELETE, GET, GET_ALL, UPDATE, CLEAR)
    public static final String OPERATION = "CamelHazelcastOperationType";

    /**
     * @deprecated
     * use {@link HazelcastOperation#PUT}
     */
    public static final String PUT_OPERATION = "put";

    /**
     * @deprecated
     * use {@link HazelcastOperation#DELETE}
     */
    public static final String DELETE_OPERATION = "delete";

    /**
     * @deprecated
     * use {@link HazelcastOperation#GET}
     */
    public static final String GET_OPERATION = "get";

    /**
     * @deprecated
     * use {@link HazelcastOperation#UPDATE}
     */
    public static final String UPDATE_OPERATION = "update";

    /**
     * @deprecated
     * use {@link HazelcastOperation#QUERY}
     */
    public static final String QUERY_OPERATION = "query";

    /**
     * @deprecated
     * use {@link HazelcastOperation#GET_ALL}
     */
    public static final String GET_ALL_OPERATION = "getAll";

    /**
     * @deprecated
     * use {@link HazelcastOperation#CLEAR}
     */
    public static final String CLEAR_OPERATION = "clear";

    /**
     * @deprecated
     * use {@link HazelcastOperation#PUT_IF_ABSENT}
     */
    public static final String PUT_IF_ABSENT_OPERATION = "putIfAbsent";

    /**
     * @deprecated
     * use {@link HazelcastOperation#ADD_ALL}
     */
    public static final String ADD_ALL_OPERATION = "addAll";

    /**
     * @deprecated
     * use {@link HazelcastOperation#REMOVE_ALL}
     */
    public static final String REMOVE_ALL_OPERATION = "removeAll";

    /**
     * @deprecated
     * use {@link HazelcastOperation#RETAIN_ALL}
     */
    public static final String RETAIN_ALL_OPERATION = "retailAll";

    /**
     * @deprecated
     * use {@link HazelcastOperation#EVICT}
     */
    public static final String EVICT_OPERATION = "evict";

    /**
     * @deprecated
     * use {@link HazelcastOperation#EVICT_ALL}
     */
    public static final String EVICT_ALL_OPERATION = "evictAll";

    /**
     * @deprecated
     * use {@link HazelcastOperation#VALUE_COUNT}
     */
    public static final String VALUE_COUNT_OPERATION = "valueCount";

    /**
     * @deprecated
     * use {@link HazelcastOperation#CONTAINS_KEY}
     */
    public static final String CONTAINS_KEY_OPERATION = "containsKey";

    /**
     * @deprecated
     * use {@link HazelcastOperation#CONTAINS_VALUE}
     */
    public static final String CONTAINS_VALUE_OPERATION = "containsValue";

    /**
     * @deprecated
     * use {@link HazelcastOperation#GET_KEYS}
     */
    public static final String GET_KEYS_OPERATION = "keySet";
    
    // multimap
    /**
     * @deprecated
     * use {@link HazelcastOperation#REMOVE_VALUE}
     */
    public static final String REMOVEVALUE_OPERATION = "removevalue";

    // atomic numbers
    /**
     * @deprecated
     * use {@link HazelcastOperation#INCREMENT}
     */
    public static final String INCREMENT_OPERATION = "increment";

    /**
     * @deprecated
     * use {@link HazelcastOperation#DECREMENT}
     */
    public static final String DECREMENT_OPERATION = "decrement";

    /**
     * @deprecated
     * use {@link HazelcastOperation#SET_VALUE}
     */
    public static final String SETVALUE_OPERATION = "setvalue";

    /**
     * @deprecated
     * use {@link HazelcastOperation#DESTROY}
     */
    public static final String DESTROY_OPERATION = "destroy";

    /**
     * @deprecated
     * use {@link HazelcastOperation#COMPARE_AND_SET}
     */
    public static final String COMPARE_AND_SET_OPERATION = "compareAndSet";

    /**
     * @deprecated
     * use {@link HazelcastOperation#GET_AND_ADD}
     */
    public static final String GET_AND_ADD_OPERATION = "getAndAdd";

    // queue
    /**
     * @deprecated
     * use {@link HazelcastOperation#ADD}
     */
    public static final String ADD_OPERATION = "add";

    /**
     * @deprecated
     * use {@link HazelcastOperation#OFFER}
     */
    public static final String OFFER_OPERATION = "offer";

    /**
     * @deprecated
     * use {@link HazelcastOperation#PEEK}
     */
    public static final String PEEK_OPERATION = "peek";

    /**
     * @deprecated
     * use {@link HazelcastOperation#POLL}
     */
    public static final String POLL_OPERATION = "poll";

    /**
     * @deprecated
     * use {@link HazelcastOperation#REMAINING_CAPACITY}
     */
    public static final String REMAINING_CAPACITY_OPERATION = "remainingCapacity";

    /**
     * @deprecated
     * use {@link HazelcastOperation#DRAIN_TO}
     */
    public static final String DRAIN_TO_OPERATION = "drainTo";

    // topic
    /**
     * @deprecated
     * use {@link HazelcastOperation#PUBLISH}
     */
    public static final String PUBLISH_OPERATION = "publish";
    
    // ring_buffer
    /**
     * @deprecated
     * use {@link HazelcastOperation#READ_ONCE_HEAD}
     */
    public static final String READ_ONCE_HEAD_OPERATION = "readOnceHead";

    /**
     * @deprecated
     * use {@link HazelcastOperation#READ_ONCE_TAIL}
     */
    public static final String READ_ONCE_TAIL_OPERATION = "readOnceTail";

    /**
     * @deprecated
     * use {@link HazelcastOperation#CAPACITY}
     */
    public static final String GET_CAPACITY_OPERATION = "capacity";

    /*
     * header values
     */

    // listener actions
    public static final String REMOVED = "removed";
    public static final String EVICTED = "evicted";
    public static final String UPDATED = "updated";
    public static final String ADDED = "added";

    // message listener actions (topic)
    public static final String RECEIVED = "received";

    // storage types (map, queue, topic, multimap)
    public static final String MAP = "map";
    public static final String MULTIMAP = "multimap";
    public static final String ATOMICNUMBER = "atomicnumber";
    public static final String QUEUE = "queue";

    // listener types
    public static final String CACHE_LISTENER = "cachelistener";
    public static final String INSTANCE_LISTENER = "instancelistener";
    public static final String ITEM_LISTENER = "itemlistener";

    // parameter names
    public static final String OPERATION_PARAM = "operation";
    public static final String HAZELCAST_INSTANCE_NAME_PARAM = "hazelcastInstanceName";
    public static final String HAZELCAST_INSTANCE_PARAM = "hazelcastInstance";
    public static final String HAZELCAST_CONFIGU_PARAM = "hazelcastConfig";
    public static final String HAZELCAST_CONFIGU_URI_PARAM = "hazelcastConfigUri";
    
    // Hazelcast mode
    public static final String HAZELCAST_NODE_MODE = "node";
    public static final String HAZELCAST_CLIENT_MODE = "client";


    private HazelcastConstants() {
    }

}
