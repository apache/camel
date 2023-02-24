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
package org.apache.camel.impl.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.EndpointRegistry;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.NormalizedUri;

/**
 * A provisional (temporary) {@link EndpointRegistry} that is only used during startup of Apache Camel to make starting
 * Camel faster while {@link LRUCacheFactory} is warming up etc.
 */
class ProvisionalEndpointRegistry extends HashMap<NormalizedUri, Endpoint> implements EndpointRegistry<NormalizedUri> {

    @Override
    public void start() {
        // noop
    }

    @Override
    public void stop() {
        // noop
    }

    @Override
    public int staticSize() {
        return 0;
    }

    @Override
    public int dynamicSize() {
        return 0;
    }

    @Override
    public int getMaximumCacheSize() {
        return 0;
    }

    @Override
    public void purge() {
        // noop
    }

    @Override
    public boolean isStatic(String key) {
        return false;
    }

    @Override
    public boolean isDynamic(String key) {
        return false;
    }

    @Override
    public void cleanUp() {
        // noop
    }

    @Override
    public Collection<Endpoint> getReadOnlyValues() {
        if (isEmpty()) {
            return Collections.emptyList();
        }

        // we want to avoid any kind of locking in get/put methods
        // as getReadOnlyValues is only seldom used, such as when camel-mock
        // is asserting endpoints at end of testing
        // so this code will then just retry in case of a concurrency update
        Collection<Endpoint> answer = new ArrayList<>();
        boolean done = false;
        while (!done) {
            try {
                answer.addAll(values());
                done = true;
            } catch (ConcurrentModificationException e) {
                answer.clear();
                // try again
            }
        }
        return Collections.unmodifiableCollection(answer);
    }

    @Override
    public Map<String, Endpoint> getReadOnlyMap() {
        if (isEmpty()) {
            return Collections.emptyMap();
        }

        // we want to avoid any kind of locking in get/put methods
        // as getReadOnlyValues is only seldom used, such as when camel-mock
        // is asserting endpoints at end of testing
        // so this code will then just retry in case of a concurrency update
        Map<String, Endpoint> answer = new LinkedHashMap<>();
        boolean done = false;
        while (!done) {
            try {
                for (Entry<NormalizedUri, Endpoint> entry : entrySet()) {
                    String k = entry.getKey().toString();
                    answer.put(k, entry.getValue());
                }
                done = true;
            } catch (ConcurrentModificationException e) {
                answer.clear();
                // try again
            }
        }
        return Collections.unmodifiableMap(answer);
    }
}
