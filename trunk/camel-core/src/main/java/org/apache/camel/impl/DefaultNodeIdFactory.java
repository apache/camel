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
package org.apache.camel.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.NamedNode;
import org.apache.camel.spi.NodeIdFactory;

/**
 * Default id factory.
 *
 * @version 
 */
public class DefaultNodeIdFactory implements NodeIdFactory {

    protected static Map<String, AtomicInteger> nodeCounters = new HashMap<String, AtomicInteger>();

    public String createId(NamedNode definition) {
        String key = definition.getShortName();
        return key + getNodeCounter(key).incrementAndGet();
    }

    /**
     * Returns the counter for the given node key, lazily creating one if necessary
     */
    protected static synchronized AtomicInteger getNodeCounter(String key) {
        AtomicInteger answer = nodeCounters.get(key);
        if (answer == null) {
            answer = new AtomicInteger(0);
            nodeCounters.put(key, answer);
        }
        return answer;
    }


    /**
     * Helper method for test purposes that allows tests to start clean (made protected 
     *  to ensure that it is not called accidentally)
     */
    protected static synchronized void resetAllCounters() {
        for (AtomicInteger counter : nodeCounters.values()) {
            counter.set(0);
        }
    }
}
