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
package org.apache.camel.component.hazelcast.map;

import java.util.Collection;
import java.util.Map;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.IMap;
import com.hazelcast.query.SqlPredicate;

import org.apache.camel.Exchange;
import org.apache.camel.component.hazelcast.HazelcastComponentHelper;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.apache.camel.impl.DefaultProducer;

public class HazelcastMapProducer extends DefaultProducer {

    private final IMap<String, Object> cache;
    private final HazelcastComponentHelper helper = new HazelcastComponentHelper();

    public HazelcastMapProducer(HazelcastMapEndpoint endpoint, String cacheName) {
        super(endpoint);
        this.cache = Hazelcast.getMap(cacheName);
    }

    public void process(Exchange exchange) throws Exception {

        Map<String, Object> headers = exchange.getIn().getHeaders();

        // get header parameters
        String oid = null;
        int operation = -1;
        String query = null;

        if (headers.containsKey(HazelcastConstants.OBJECT_ID)) {
            oid = (String) headers.get(HazelcastConstants.OBJECT_ID);
        }

        if (headers.containsKey(HazelcastConstants.OPERATION)) {

            // producer allows int (HazelcastConstants) and string values
            if (headers.get(HazelcastConstants.OPERATION) instanceof String) {
                operation = helper.lookupOperationNumber((String) headers.get(HazelcastConstants.OPERATION));
            } else {
                operation = (Integer) headers.get(HazelcastConstants.OPERATION);
            }
        }

        if (headers.containsKey(HazelcastConstants.QUERY)) {
            query = (String) headers.get(HazelcastConstants.QUERY);
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

        case HazelcastConstants.UPDATE_OPERATION:
            this.update(oid, exchange);
            break;

        case HazelcastConstants.QUERY_OPERATION:
            this.query(query, exchange);
            break;

        default:
            throw new IllegalArgumentException(String.format("The value '%s' is not allowed for parameter '%s' on the MAP cache.", operation, HazelcastConstants.OPERATION));
        }

        // finally copy headers
        HazelcastComponentHelper.copyHeaders(exchange);

    }

    /**
     * query map with a sql like syntax (see http://www.hazelcast.com/)
     */
    private void query(String query, Exchange exchange) {
        Collection<Object> result = this.cache.values(new SqlPredicate(query));
        exchange.getOut().setBody(result);
    }

    /**
     * update an object in your cache (the whole object will be replaced)
     */
    private void update(String oid, Exchange exchange) {
        Object body = exchange.getIn().getBody();
        this.cache.lock(oid);
        this.cache.replace(oid, body);
        this.cache.unlock(oid);
    }

    /**
     * remove an object from the cache
     */
    private void delete(String oid) {
        this.cache.remove(oid);
    }

    /**
     * find an object by the given id and give it back
     */
    private void get(String oid, Exchange exchange) {
        exchange.getOut().setBody(this.cache.get(oid));
    }

    /**
     * put a new object into the cache
     */
    private void put(String oid, Exchange exchange) {
        Object body = exchange.getIn().getBody();
        this.cache.put(oid, body);
    }
}
