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

public enum HazelcastOperation {

    // actions
    PUT("put"),
    DELETE("delete"),
    GET("get"),
    UPDATE("update"),
    QUERY("query"),
    GET_ALL("getAll"),
    CLEAR("clear"),
    PUT_IF_ABSENT("putIfAbsent"),
    ADD_ALL("allAll"),
    REMOVE_ALL("removeAll"),
    RETAIN_ALL("retainAll"),
    EVICT("evict"),
    EVICT_ALL("evictAll"),
    VALUE_COUNT("valueCount"),
    CONTAINS_KEY("containsKey"),
    CONTAINS_VALUE("containsValue"),
    GET_KEYS("keySet"),

    // multimap
    REMOVE_VALUE("removevalue"),

    // atomic numbers
    INCREMENT("increment"),
    DECREMENT("decrement"),
    SET_VALUE("setvalue"),
    DESTROY("destroy"),
    COMPARE_AND_SET("compareAndSet"),
    GET_AND_ADD("getAndAdd"),

    // queue
    ADD("add"),
    OFFER("offer"),
    PEEK("peek"),
    POLL("poll"),
    REMAINING_CAPACITY("remainingCapacity"),
    DRAIN_TO("drainTo"),
    REMOVE_IF("removeIf"),
    TAKE("take"),
    

    // topic
    PUBLISH("publish"),

    // ring_buffer
    READ_ONCE_HEAD("readOnceHeal"),
    READ_ONCE_TAIL("readOnceTail"),
    CAPACITY("capacity");


    private static HazelcastOperation[] values = values();
    private final String operation;

    HazelcastOperation(String operation) {
        this.operation = operation;
    }

    public static HazelcastOperation getHazelcastOperation(String name) {
        if (name == null) {
            return null;
        }
        for (HazelcastOperation hazelcastOperation : values) {
            if (hazelcastOperation.toString().equalsIgnoreCase(name) || hazelcastOperation.name().equalsIgnoreCase(name)) {
                return hazelcastOperation;
            }
        }
        throw new IllegalArgumentException(String.format("Operation '%s' is not supported by this component.", name));
    }

    public String toString() {
        return operation;
    }

}
