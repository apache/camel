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
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.query.SqlPredicate;

import org.apache.camel.Exchange;
import org.apache.camel.component.hazelcast.HazelcastComponentHelper;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.apache.camel.component.hazelcast.HazelcastDefaultProducer;
import org.apache.camel.component.hazelcast.HazelcastOperation;
import org.apache.camel.util.ObjectHelper;

public class HazelcastMapProducer extends HazelcastDefaultProducer {

    private final IMap<Object, Object> cache;

    public HazelcastMapProducer(HazelcastInstance hazelcastInstance, HazelcastMapEndpoint endpoint, String cacheName) {
        super(endpoint);
        this.cache = hazelcastInstance.getMap(cacheName);
    }

    public void process(Exchange exchange) throws Exception {

        Map<String, Object> headers = exchange.getIn().getHeaders();

        // GET header parameters
        Object oid = null;
        Object ovalue = null;
        Object ttl = null;
        Object ttlUnit = null;
        String query = null;

        if (headers.containsKey(HazelcastConstants.OBJECT_ID)) {
            oid = headers.get(HazelcastConstants.OBJECT_ID);
        }
        
        if (headers.containsKey(HazelcastConstants.OBJECT_VALUE)) {
            ovalue = headers.get(HazelcastConstants.OBJECT_VALUE);
        }

        if (headers.containsKey(HazelcastConstants.TTL_VALUE)) {
            ttl = headers.get(HazelcastConstants.TTL_VALUE);
        }

        if (headers.containsKey(HazelcastConstants.TTL_UNIT)) {
            ttlUnit = headers.get(HazelcastConstants.TTL_UNIT);
        }
        
        if (headers.containsKey(HazelcastConstants.QUERY)) {
            query = (String) headers.get(HazelcastConstants.QUERY);
        }

        final HazelcastOperation operation = lookupOperation(exchange);
        switch (operation) {

        case PUT:
            if (ObjectHelper.isEmpty(ttl) && ObjectHelper.isEmpty(ttlUnit)) {
                this.put(oid, exchange);
            } else {
                this.put(oid, ttl, ttlUnit, exchange);
            }
            break;
            
        case PUT_IF_ABSENT:
            if (ObjectHelper.isEmpty(ttl) && ObjectHelper.isEmpty(ttlUnit)) {
                this.putIfAbsent(oid, exchange);
            } else {
                this.putIfAbsent(oid, ttl, ttlUnit, exchange);
            }
            break;

        case GET:
            this.get(oid, exchange);
            break;
            
        case GET_ALL:
            this.getAll(oid, exchange);
            break;

        case GET_KEYS:
            this.getKeys(exchange);
            break;

        case CONTAINS_KEY:
            this.containsKey(oid, exchange);
            break;
            
        case CONTAINS_VALUE:
            this.containsValue(exchange);
            break;

        case DELETE:
            this.delete(oid);
            break;

        case UPDATE:
            if (ObjectHelper.isEmpty(ovalue)) {
                this.update(oid, exchange);
            } else {
                this.update(oid, ovalue, exchange);
            }
            break;

        case QUERY:
            this.query(query, exchange);
            break;
            
        case CLEAR:
            this.clear(exchange);
            break;
            
        case EVICT:
            this.evict(oid);
            break;

        case EVICT_ALL:
            this.evictAll();
            break;
            
        default:
            throw new IllegalArgumentException(String.format("The value '%s' is not allowed for parameter '%s' on the MAP cache.", operation, HazelcastConstants.OPERATION));
        }

        // finally copy headers
        HazelcastComponentHelper.copyHeaders(exchange);
    }

    /**
     * QUERY map with a sql like syntax (see http://www.hazelcast.com/)
     */
    private void query(String query, Exchange exchange) {
        Collection<Object> result;
        if (ObjectHelper.isNotEmpty(query)) {
            result = this.cache.values(new SqlPredicate(query));
        } else {
            result = this.cache.values();
        }
        exchange.getOut().setBody(result);
    }

    /**
     * UPDATE an object in your cache (the whole object will be replaced)
     */
    private void update(Object oid, Exchange exchange) {
        Object body = exchange.getIn().getBody();
        this.cache.lock(oid);
        this.cache.replace(oid, body);
        this.cache.unlock(oid);
    }
    
    /**
     * Replaces the entry for given id with a specific value in the body, only if currently mapped to a given value
     */
    private void update(Object oid, Object ovalue, Exchange exchange) {
        Object body = exchange.getIn().getBody();
        this.cache.lock(oid);
        this.cache.replace(oid, ovalue, body);
        this.cache.unlock(oid);
    }

    /**
     * remove an object from the cache
     */
    private void delete(Object oid) {
        this.cache.remove(oid);
    }

    /**
     * find an object by the given id and give it back
     */
    private void get(Object oid, Exchange exchange) {
        exchange.getOut().setBody(this.cache.get(oid));
    }
    
    
    /**
     * GET All objects and give it back
     */
    private void getAll(Object oid, Exchange exchange) {
        exchange.getOut().setBody(this.cache.getAll((Set<Object>) oid));
    }

    /**
     * PUT a new object into the cache
     */
    private void put(Object oid, Exchange exchange) {
        Object body = exchange.getIn().getBody();
        this.cache.put(oid, body);
    }
    
    /**
     * PUT a new object into the cache with a specific time to live
     */
    private void put(Object oid, Object ttl, Object ttlUnit, Exchange exchange) {
        Object body = exchange.getIn().getBody();
        this.cache.put(oid, body, (long) ttl, (TimeUnit) ttlUnit);
    }
    
    /**
     * if the specified key is not already associated with a value, associate it with the given value.
     */
    private void putIfAbsent(Object oid, Exchange exchange) {
        Object body = exchange.getIn().getBody();
        this.cache.putIfAbsent(oid, body);
    }
    
    /**
     * Puts an entry into this map with a given ttl (time to live) value if the specified key is not already associated with a value.
     */
    private void putIfAbsent(Object oid, Object ttl, Object ttlUnit, Exchange exchange) {
        Object body = exchange.getIn().getBody();
        this.cache.putIfAbsent(oid, body, (long) ttl, (TimeUnit) ttlUnit);
    }
    
    /**
     * Clear all the entries
     */
    private void clear(Exchange exchange) {
        this.cache.clear();
    }
    
    /**
     * Eviction operation for a specific key
     */
    private void evict(Object oid) {
        this.cache.evict(oid);
    }
    
    /**
     * Evict All operation 
     */
    private void evictAll() {
        this.cache.evictAll();
    }
    
    /**
     * Check for a specific key in the cache and return true if it exists or false otherwise
     */
    private void containsKey(Object oid, Exchange exchange) {
        exchange.getOut().setBody(this.cache.containsKey(oid));
    }
    
    /**
     * Check for a specific value in the cache and return true if it exists or false otherwise
     */
    private void containsValue(Exchange exchange) {
        Object body = exchange.getIn().getBody();
        exchange.getOut().setBody(this.cache.containsValue(body));
    }

    /**
    * GET keys set of objects and give it back
    */
    private void getKeys(Exchange exchange) {
        exchange.getOut().setBody(this.cache.keySet());
    }
}
