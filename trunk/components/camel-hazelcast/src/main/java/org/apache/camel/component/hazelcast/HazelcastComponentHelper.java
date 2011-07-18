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

public class HazelcastComponentHelper {

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

        // set out headers
        ex.getOut().setHeaders(headers);
    }

    public static void setListenerHeaders(Exchange ex, String listenerType, String listenerAction, String cacheName) {
        ex.getOut().setHeader(HazelcastConstants.CACHE_NAME, cacheName);
        HazelcastComponentHelper.setListenerHeaders(ex, listenerType, listenerAction);
    }

    public static void setListenerHeaders(Exchange ex, String listenerType, String listenerAction) {
        ex.getOut().setHeader(HazelcastConstants.LISTENER_ACTION, listenerAction);
        ex.getOut().setHeader(HazelcastConstants.LISTENER_TYPE, listenerType);
        ex.getOut().setHeader(HazelcastConstants.LISTENER_TIME, new Date().getTime());
    }

    /**
     * Allows the use of speaking operation names (e.g. for usage in Spring DSL)
     */
    public int lookupOperationNumber(String operation) {
        if (this.mapping.containsKey(operation)) {
            return this.mapping.get(operation);
        } else {
            throw new IllegalArgumentException(String.format("Operation '%s' is not supported by this component.", operation));
        }
    }

    private void init() {
        // fill map with values
        this.mapping.put("put", HazelcastConstants.PUT_OPERATION);
        this.mapping.put("delete", HazelcastConstants.DELETE_OPERATION);
        this.mapping.put("get", HazelcastConstants.GET_OPERATION);
        this.mapping.put("update", HazelcastConstants.UPDATE_OPERATION);
        this.mapping.put("query", HazelcastConstants.QUERY_OPERATION);

        // multimap
        this.mapping.put("removevalue", HazelcastConstants.REMOVEVALUE_OPERATION);

        // atomic numbers
        this.mapping.put("increment", HazelcastConstants.INCREMENT_OPERATION);
        this.mapping.put("decrement", HazelcastConstants.DECREMENT_OPERATION);
        this.mapping.put("setvalue", HazelcastConstants.SETVALUE_OPERATION);
        this.mapping.put("destroy", HazelcastConstants.DESTROY_OPERATION);

        // queue
        this.mapping.put("add", HazelcastConstants.ADD_OPERATION);
        this.mapping.put("offer", HazelcastConstants.OFFER_OPERATION);
        this.mapping.put("peek", HazelcastConstants.PEEK_OPERATION);
        this.mapping.put("poll", HazelcastConstants.POLL_OPERATION);
    }

}
