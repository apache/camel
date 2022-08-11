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

package org.apache.camel.test.infra.activemq.services;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.util.URISupport;

public final class LegacyEmbeddedBroker {
    private static AtomicInteger counter = new AtomicInteger();

    private LegacyEmbeddedBroker() {

    }

    @Deprecated
    public static String createBrokerUrl() {
        return createBrokerUrl(null);
    }

    @Deprecated
    public static String createBrokerUrl(String options) {
        // using a unique broker name improves testing when running the entire test suite in the same JVM
        int id = counter.incrementAndGet();
        Map<String, Object> map = new HashMap<>();
        map.put("broker.useJmx", false);
        map.put("broker.persistent", false);
        return createUri("vm://test-broker-" + id, map, options);
    }

    @Deprecated
    private static String createUri(String uri, Map<String, Object> map, String options) {
        try {
            map.putAll(URISupport.parseQuery(options));
            return URISupport.appendParametersToURI(uri, map);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

}
