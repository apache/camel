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
    public static final String ATOMICNUMBER_PREFIX = "atomicvalue:";
    public static final String INSTANCE_PREFIX = "instance:";
    public static final String QUEUE_PREFIX = "queue:";
    public static final String SEDA_PREFIX = "seda:";
    public static final String LIST_PREFIX = "list:";

    /*
     * incoming header properties
     */
    public static final String OBJECT_ID = "hazelcast.objectId";
    public static final String OBJECT_POS = "hazelcast.objectIndex";
    public static final String QUERY = "hazelcast.query";

    /*
     * outgoing header properties
     */
    public static final String LISTENER_ACTION = "hazelcast.listener.action";
    public static final String LISTENER_TYPE = "hazelcast.listener.type";
    public static final String LISTENER_TIME = "hazelcast.listener.time";
    public static final String INSTANCE_HOST = "hazelcast.instance.host";
    public static final String INSTANCE_PORT = "hazelcast.instance.port";
    public static final String CACHE_NAME = "hazelcast.cache.name";
    public static final String CACHE_TYPE = "hazelcast.cache.type";

    // actions (put, delete, get, update)
    public static final String OPERATION = "hazelcast.operation.type";
    public static final int PUT_OPERATION = 1;
    public static final int DELETE_OPERATION = 2;
    public static final int GET_OPERATION = 3;
    public static final int UPDATE_OPERATION = 4;
    public static final int QUERY_OPERATION = 5;

    // multimap
    public static final int REMOVEVALUE_OPERATION = 10;

    // atomic numbers
    public static final int INCREMENT_OPERATION = 20;
    public static final int DECREMENT_OPERATION = 21;
    public static final int SETVALUE_OPERATION = 22;
    public static final int DESTROY_OPERATION = 23;

    // queue
    public static final int ADD_OPERATION = 31;
    public static final int OFFER_OPERATION = 32;
    public static final int PEEK_OPERATION = 33;
    public static final int POLL_OPERATION = 34;

    /*
     * header values
     */

    // listener actions
    public static final String REMOVED = "removed";
    public static final String ENVICTED = "envicted";
    public static final String UPDATED = "updated";
    public static final String ADDED = "added";

    // storage types (map, queue, topic, multimap)
    public static final String MAP = "map";
    public static final String MULTIMAP = "multimap";
    public static final String ATOMICNUMBER = "atomicnumber";
    public static final String QUEUE = "queue";

    // listener types
    public static final String CACHE_LISTENER = "cachelistener";
    public static final String INSTANCE_LISTENER = "instancelistener";
    public static final String ITEM_LISTENER = "itemlistener";

    private HazelcastConstants() {
    }

}
