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

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;

public final class HazelcastComponentHelper {

    private static final HashMap<String, Integer> MAPPING;

    static {
        MAPPING = new HashMap<>();
        addMapping(MAPPING, "put", HazelcastConstants.PUT_OPERATION);
        addMapping(MAPPING, "delete", HazelcastConstants.DELETE_OPERATION);
        addMapping(MAPPING, "get", HazelcastConstants.GET_OPERATION);
        addMapping(MAPPING, "update", HazelcastConstants.UPDATE_OPERATION);
        addMapping(MAPPING, "query", HazelcastConstants.QUERY_OPERATION);
        addMapping(MAPPING, "getAll", HazelcastConstants.GET_ALL_OPERATION);
        addMapping(MAPPING, "clear", HazelcastConstants.CLEAR_OPERATION);
        addMapping(MAPPING, "evict", HazelcastConstants.EVICT_OPERATION);
        addMapping(MAPPING, "evictAll", HazelcastConstants.EVICT_ALL_OPERATION);
        addMapping(MAPPING, "putIfAbsent", HazelcastConstants.PUT_IF_ABSENT_OPERATION);
        addMapping(MAPPING, "addAll", HazelcastConstants.ADD_ALL_OPERATION);
        addMapping(MAPPING, "removeAll", HazelcastConstants.REMOVE_ALL_OPERATION);
        addMapping(MAPPING, "retainAll", HazelcastConstants.RETAIN_ALL_OPERATION);
        addMapping(MAPPING, "valueCount", HazelcastConstants.VALUE_COUNT_OPERATION);
        addMapping(MAPPING, "containsKey", HazelcastConstants.CONTAINS_KEY_OPERATION);
        addMapping(MAPPING, "containsValue", HazelcastConstants.CONTAINS_VALUE_OPERATION);
        addMapping(MAPPING, "keySet", HazelcastConstants.GET_KEYS_OPERATION);

        // multimap
        addMapping(MAPPING, "removevalue", HazelcastConstants.REMOVEVALUE_OPERATION);

        // atomic numbers
        addMapping(MAPPING, "increment", HazelcastConstants.INCREMENT_OPERATION);
        addMapping(MAPPING, "decrement", HazelcastConstants.DECREMENT_OPERATION);
        addMapping(MAPPING, "setvalue", HazelcastConstants.SETVALUE_OPERATION);
        addMapping(MAPPING, "destroy", HazelcastConstants.DESTROY_OPERATION);
        addMapping(MAPPING, "compareAndSet", HazelcastConstants.COMPARE_AND_SET_OPERATION);
        addMapping(MAPPING, "getAndAdd", HazelcastConstants.GET_AND_ADD_OPERATION);

        // queue
        addMapping(MAPPING, "add", HazelcastConstants.ADD_OPERATION);
        addMapping(MAPPING, "offer", HazelcastConstants.OFFER_OPERATION);
        addMapping(MAPPING, "peek", HazelcastConstants.PEEK_OPERATION);
        addMapping(MAPPING, "poll", HazelcastConstants.POLL_OPERATION);
        addMapping(MAPPING, "remainingCapacity", HazelcastConstants.REMAINING_CAPACITY_OPERATION);
        addMapping(MAPPING, "drainTo", HazelcastConstants.DRAIN_TO_OPERATION);

        // topic
        addMapping(MAPPING, "publish", HazelcastConstants.PUBLISH_OPERATION);

        // ringbuffer
        addMapping(MAPPING, "capacity", HazelcastConstants.GET_CAPACITY_OPERATION);
        addMapping(MAPPING, "readonceHead", HazelcastConstants.READ_ONCE_HEAD_OPERATION);
        addMapping(MAPPING, "readonceTail", HazelcastConstants.READ_ONCE_TAIL_OPERATION);
    }

    private HazelcastComponentHelper() {
    }

    public static void copyHeaders(Exchange ex) {
        // get in headers
        Map<String, Object> headers = ex.getIn().getHeaders();

        // delete item id
        if (headers.containsKey(HazelcastConstants.OBJECT_ID)) {
            headers.remove(HazelcastConstants.OBJECT_ID);
        }

        if (headers.containsKey(HazelcastConstants.OPERATION)) {
            headers.remove(HazelcastConstants.OPERATION);
        }

        // propagate headers if OUT message created
        if (ex.hasOut()) {
            ex.getOut().setHeaders(headers);
        }
    }

    public static void setListenerHeaders(Exchange ex, String listenerType, String listenerAction, String cacheName) {
        ex.getIn().setHeader(HazelcastConstants.CACHE_NAME, cacheName);
        HazelcastComponentHelper.setListenerHeaders(ex, listenerType, listenerAction);
    }

    public static void setListenerHeaders(Exchange ex, String listenerType, String listenerAction) {
        ex.getIn().setHeader(HazelcastConstants.LISTENER_ACTION, listenerAction);
        ex.getIn().setHeader(HazelcastConstants.LISTENER_TYPE, listenerType);
        ex.getIn().setHeader(HazelcastConstants.LISTENER_TIME, new Date().getTime());
    }

    public static int lookupOperationNumber(Exchange exchange, int defaultOperation) {
        return extractOperationNumber(exchange.getIn().getHeader(HazelcastConstants.OPERATION), defaultOperation);
    }

    public static int extractOperationNumber(Object value, int defaultOperation) {
        int operation = defaultOperation;
        if (value instanceof String) {
            operation = mapToOperationNumber((String) value);
        } else if (value instanceof Integer) {
            operation = (Integer)value;
        }
        return operation;
    }

    /**
     * Allows the use of speaking operation names (e.g. for usage in Spring DSL)
     */
    private static int mapToOperationNumber(String operationName) {
        if (MAPPING.containsKey(operationName)) {
            return MAPPING.get(operationName);
        } else {
            throw new IllegalArgumentException(String.format("Operation '%s' is not supported by this component.", operationName));
        }
    }

    private static void addMapping(HashMap<String, Integer> mapping, String operationName, int operationNumber) {
        mapping.put(operationName, operationNumber);
        mapping.put(String.valueOf(operationNumber), operationNumber);
    }
}
