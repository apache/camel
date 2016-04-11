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

    private final HashMap<String, Integer> mapping = new HashMap<String, Integer>();

    public HazelcastComponentHelper() {
        this.init();
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

    public int lookupOperationNumber(Exchange exchange, int defaultOperation) {
        return extractOperationNumber(exchange.getIn().getHeader(HazelcastConstants.OPERATION), defaultOperation);
    }

    public int extractOperationNumber(Object value, int defaultOperation) {
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
    private int mapToOperationNumber(String operationName) {
        if (this.mapping.containsKey(operationName)) {
            return this.mapping.get(operationName);
        } else {
            throw new IllegalArgumentException(String.format("Operation '%s' is not supported by this component.", operationName));
        }
    }

    private void init() {
        // fill map with values
        addMapping("put", HazelcastConstants.PUT_OPERATION);
        addMapping("delete", HazelcastConstants.DELETE_OPERATION);
        addMapping("get", HazelcastConstants.GET_OPERATION);
        addMapping("update", HazelcastConstants.UPDATE_OPERATION);
        addMapping("query", HazelcastConstants.QUERY_OPERATION);
        addMapping("getAll", HazelcastConstants.GET_ALL_OPERATION);
        addMapping("clear", HazelcastConstants.CLEAR_OPERATION);
        addMapping("evict", HazelcastConstants.EVICT_OPERATION);
        addMapping("evictAll", HazelcastConstants.EVICT_ALL_OPERATION);
        addMapping("putIfAbsent", HazelcastConstants.PUT_IF_ABSENT_OPERATION);
        addMapping("addAll", HazelcastConstants.ADD_ALL_OPERATION);
        addMapping("removeAll", HazelcastConstants.REMOVE_ALL_OPERATION);
        addMapping("retainAll", HazelcastConstants.RETAIN_ALL_OPERATION);
        addMapping("valueCount", HazelcastConstants.VALUE_COUNT_OPERATION);
        addMapping("containsKey", HazelcastConstants.CONTAINS_KEY_OPERATION);
        addMapping("containsValue", HazelcastConstants.CONTAINS_VALUE_OPERATION);

        // multimap
        addMapping("removevalue", HazelcastConstants.REMOVEVALUE_OPERATION);

        // atomic numbers
        addMapping("increment", HazelcastConstants.INCREMENT_OPERATION);
        addMapping("decrement", HazelcastConstants.DECREMENT_OPERATION);
        addMapping("setvalue", HazelcastConstants.SETVALUE_OPERATION);
        addMapping("destroy", HazelcastConstants.DESTROY_OPERATION);
        addMapping("compareAndSet", HazelcastConstants.COMPARE_AND_SET_OPERATION);
        addMapping("getAndAdd", HazelcastConstants.GET_AND_ADD_OPERATION);

        // queue
        addMapping("add", HazelcastConstants.ADD_OPERATION);
        addMapping("offer", HazelcastConstants.OFFER_OPERATION);
        addMapping("peek", HazelcastConstants.PEEK_OPERATION);
        addMapping("poll", HazelcastConstants.POLL_OPERATION);
        addMapping("remainingCapacity", HazelcastConstants.REMAINING_CAPACITY_OPERATION);
        addMapping("drainTo", HazelcastConstants.DRAIN_TO_OPERATION);

        // topic
        addMapping("publish", HazelcastConstants.PUBLISH_OPERATION);
        
        // ringbuffer
        addMapping("capacity", HazelcastConstants.GET_CAPACITY_OPERATION);
        addMapping("readonceHead", HazelcastConstants.READ_ONCE_HEAD_OPERATION);
        addMapping("readonceTail", HazelcastConstants.READ_ONCE_TAIL_OPERATION);
    }

    private void addMapping(String operationName, int operationNumber) {
        this.mapping.put(operationName, operationNumber);
        this.mapping.put(String.valueOf(operationNumber), operationNumber);
    }

}
