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

    // actions (put, delete, get, getAll, update, clear)
    public static final String OPERATION = "CamelHazelcastOperationType";
    public static final int PUT_OPERATION = 1;
    public static final int DELETE_OPERATION = 2;
    public static final int GET_OPERATION = 3;
    public static final int UPDATE_OPERATION = 4;
    public static final int QUERY_OPERATION = 5;
    public static final int GET_ALL_OPERATION = 6;
    public static final int CLEAR_OPERATION = 7;
    public static final int PUT_IF_ABSENT_OPERATION = 8;
    public static final int ADD_ALL_OPERATION = 9;
    public static final int REMOVE_ALL_OPERATION = 10;
    public static final int RETAIN_ALL_OPERATION = 11;
    public static final int EVICT_OPERATION = 12;
    public static final int EVICT_ALL_OPERATION = 13;
    public static final int VALUE_COUNT_OPERATION = 14;
    public static final int CONTAINS_KEY_OPERATION = 15;
    public static final int CONTAINS_VALUE_OPERATION = 16;
    
    // multimap
    public static final int REMOVEVALUE_OPERATION = 17;

    // atomic numbers
    public static final int INCREMENT_OPERATION = 20;
    public static final int DECREMENT_OPERATION = 21;
    public static final int SETVALUE_OPERATION = 22;
    public static final int DESTROY_OPERATION = 23;
    public static final int COMPARE_AND_SET_OPERATION = 24;
    public static final int GET_AND_ADD_OPERATION = 25;

    // queue
    public static final int ADD_OPERATION = 31;
    public static final int OFFER_OPERATION = 32;
    public static final int PEEK_OPERATION = 33;
    public static final int POLL_OPERATION = 34;
    public static final int REMAINING_CAPACITY_OPERATION = 35;
    public static final int DRAIN_TO_OPERATION = 36;

    // topic
    public static final int PUBLISH_OPERATION = 37;
    
    // ring_buffer
    public static final int READ_ONCE_HEAD_OPERATION = 38;
    public static final int READ_ONCE_TAIL_OPERATION = 39;
    public static final int GET_CAPACITY_OPERATION = 40;

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


    private HazelcastConstants() {
    }

}
