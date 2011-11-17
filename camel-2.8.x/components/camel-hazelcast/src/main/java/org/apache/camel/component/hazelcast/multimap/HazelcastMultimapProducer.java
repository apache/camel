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
package org.apache.camel.component.hazelcast.multimap;

import java.util.Map;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.MultiMap;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.hazelcast.HazelcastComponentHelper;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.apache.camel.impl.DefaultProducer;

public class HazelcastMultimapProducer extends DefaultProducer {

    private final MultiMap<Object, Object> cache;
    private final HazelcastComponentHelper helper = new HazelcastComponentHelper();

    public HazelcastMultimapProducer(Endpoint endpoint, String cacheName) {
        super(endpoint);
        this.cache = Hazelcast.getMultiMap(cacheName);
    }

    public void process(Exchange exchange) throws Exception {

        Map<String, Object> headers = exchange.getIn().getHeaders();

        // get header parameters
        String oid = null;
        int operation = -1;

        if (headers.containsKey(HazelcastConstants.OBJECT_ID)) {
            oid = (String) headers.get(HazelcastConstants.OBJECT_ID);
        }

        if (headers.containsKey(HazelcastConstants.OPERATION)) {
            if (headers.get(HazelcastConstants.OPERATION) instanceof String) {
                operation = helper.lookupOperationNumber((String) headers.get(HazelcastConstants.OPERATION));
            } else {
                operation = (Integer) headers.get(HazelcastConstants.OPERATION);
            }
        }

        switch (operation) {
        case HazelcastConstants.PUT_OPERATION:
            this.put(oid, exchange);
            break;

        case HazelcastConstants.GET_OPERATION:
            this.get(oid, exchange);
            break;

        case HazelcastConstants.DELETE_OPERATION:
            this.delete(oid);
            break;

        case HazelcastConstants.REMOVEVALUE_OPERATION:
            this.removevalue(oid, exchange);
            break;

        default:
            throw new IllegalArgumentException(String.format("The value '%s' is not allowed for parameter '%s' on the MULTIMAP cache.", operation, HazelcastConstants.OPERATION));
        }

        // finally copy headers
        HazelcastComponentHelper.copyHeaders(exchange);
    }

    private void put(String oid, Exchange exchange) {
        Object body = exchange.getIn().getBody();
        this.cache.put(oid, body);
    }

    private void get(String oid, Exchange exchange) {
        exchange.getOut().setBody(this.cache.get(oid));
    }

    private void delete(String oid) {
        this.cache.remove(oid);
    }

    private void removevalue(String oid, Exchange exchange) {
        this.cache.remove(oid, exchange.getIn().getBody());
    }

}
